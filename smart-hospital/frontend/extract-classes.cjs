const fs = require('fs');
const path = require('path');
const dir = 'src';
const set = new Set();
function walk(d) {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    const st = fs.statSync(p);
    if (st.isDirectory()) walk(p);
    else if (/\.(tsx|ts)$/.test(f)) {
      const t = fs.readFileSync(p, 'utf8');
      const re = /className=(?:"([^"]*)"|\{`([^`]*)`\}|\{'([^']*)'\})/g;
      let m;
      while ((m = re.exec(t))) {
        const raw = m[1] || m[2] || m[3] || '';
        for (const part of raw.split(/\s+/)) {
          const c = part.replace(/\$\{[^}]*\}/g, '').replace(/[^A-Za-z0-9_-]/g, '');
          if (c) set.add(c);
        }
      }
    }
  }
}
walk(dir);
console.log([...set].sort().join('\n'));
