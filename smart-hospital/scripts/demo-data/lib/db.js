'use strict';

/*
 * Thin MySQL client used by the demo-data generator. Everything is executed
 * through the official mysql command-line client with --login-path so no
 * credentials ever appear in the repository or in process arguments.
 */

const cp = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { sqlLiteral, chunk } = require('./util');

const DEFAULT_MYSQL = 'C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe';

function parseArgs(argv) {
  const options = {};
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (!token.startsWith('--')) continue;
    const key = token.slice(2);
    const next = argv[i + 1];
    if (next !== undefined && !next.startsWith('--')) {
      options[key] = next;
      i += 1;
    } else {
      options[key] = true;
    }
  }
  return options;
}

function createDb(options) {
  const mysql = options.mysql || process.env.SMART_HOSPITAL_MYSQL || DEFAULT_MYSQL;
  const loginPath = options['login-path'] || process.env.SMART_HOSPITAL_LOGIN_PATH || 'smart-hospital-local';
  const database = 'hospital';
  const quiet = Boolean(options.quiet);
  const sqlOut = options['sql-out'] || null;

  const baseArgs = ['--login-path=' + loginPath, '--default-character-set=utf8mb4', '--batch'];

  function log(message) {
    if (!quiet) process.stdout.write(message + '\n');
  }

  function query(sql) {
    const result = cp.spawnSync(mysql, baseArgs.concat(['-N', '-B', '-e', sql, database]), {
      encoding: 'buffer',
      maxBuffer: 128 * 1024 * 1024
    });
    if (result.error) throw result.error;
    if (result.status !== 0) {
      throw new Error('MySQL query failed:\n' + result.stderr.toString('utf8') + '\nSQL: ' + sql.slice(0, 400));
    }
    const text = result.stdout.toString('utf8').replace(/\r\n/g, '\n').trim();
    if (!text) return [];
    return text.split('\n').map((line) => {
      return line.split('\t').map((value) => (value === 'NULL' ? null : value));
    });
  }

  function scalar(sql) {
    const rows = query(sql);
    return rows.length && rows[0].length ? rows[0][0] : null;
  }

  function execStatements(statements, label) {
    if (!statements.length) return;
    const body = statements.join('\n');
    if (sqlOut) {
      fs.mkdirSync(sqlOut, { recursive: true });
      const file = path.join(sqlOut, (label || 'phase') + '.sql');
      fs.writeFileSync(file, 'SET NAMES utf8mb4;\n' + body + '\n', 'utf8');
    }
    const payload = 'SET NAMES utf8mb4;\nSTART TRANSACTION;\n' + body + '\nCOMMIT;\n';
    const temp = path.join(os.tmpdir(), 'smart-hospital-seed-' + process.pid + '.sql');
    fs.writeFileSync(temp, payload, 'utf8');
    try {
      const result = cp.spawnSync(mysql, baseArgs.concat(['--database=' + database]), {
        input: fs.readFileSync(temp),
        maxBuffer: 128 * 1024 * 1024
      });
      if (result.error) throw result.error;
      if (result.status !== 0) {
        throw new Error('MySQL execution failed for "' + label + '":\n' + result.stderr.toString('utf8'));
      }
    } finally {
      try { fs.unlinkSync(temp); } catch (ignored) { /* best effort */ }
    }
  }

  function insertSql(table, columns, rows, batchSize) {
    const statements = [];
    for (const group of chunk(rows, batchSize || 400)) {
      const values = group.map((row) => '(' + columns.map((column) => {
        if (!(column in row)) throw new Error(table + ': missing column ' + column);
        return sqlLiteral(row[column]);
      }).join(',') + ')').join(',\n');
      statements.push('INSERT INTO `' + table + '` (`' + columns.join('`,`') + '`) VALUES\n' + values + ';');
    }
    return statements;
  }

  function insert(table, columns, rows, batchSize) {
    const statements = insertSql(table, columns, rows, batchSize);
    execStatements(statements, table);
    log('  ' + table + ': ' + rows.length + ' rows');
  }

  function phase(label, statements, rowCount) {
    execStatements(statements, label);
    log('  ' + label + (rowCount === undefined ? '' : ': ' + rowCount + ' rows'));
  }

  function count(table, where) {
    const rows = query('SELECT COUNT(*) FROM `hospital`.`' + table + '`' + (where ? ' WHERE ' + where : ''));
    return Number(rows[0][0]);
  }

  return { mysql, loginPath, database, query, scalar, execStatements, insertSql, insert, phase, count, log };
}

module.exports = { createDb, parseArgs, DEFAULT_MYSQL };
