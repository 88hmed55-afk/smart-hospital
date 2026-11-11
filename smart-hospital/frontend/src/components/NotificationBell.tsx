import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Bell, FlaskConical, Inbox } from 'lucide-react';
import { apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import type { NotificationItem, NotificationUnreadCount, PageResponse } from '../types/api';

const dateText = (value: string) => new Intl.DateTimeFormat('ar-YE', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));

export default function NotificationBell() {
  const { token } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const count = useQuery({
    queryKey: ['notification-unread-count'],
    queryFn: () => apiRequest<NotificationUnreadCount>('/notifications/unread-count', token!),
    enabled: Boolean(token),
    refetchInterval: 60_000,
  });
  const items = useQuery({
    queryKey: ['notifications-recent'],
    queryFn: () => apiRequest<PageResponse<NotificationItem>>('/notifications?offset=0&limit=8', token!),
    enabled: Boolean(token && open),
  });
  const markRead = useMutation({
    mutationFn: (id: string) => apiRequest<NotificationUnreadCount>(`/notifications/${id}/read`, token!, { method: 'POST' }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notification-unread-count'] });
      await queryClient.invalidateQueries({ queryKey: ['notifications-recent'] });
    },
  });
  const markAll = useMutation({
    mutationFn: () => apiRequest<NotificationUnreadCount>('/notifications/read-all', token!, { method: 'POST' }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['notification-unread-count'] });
      await queryClient.invalidateQueries({ queryKey: ['notifications-recent'] });
    },
  });

  function openNotification(item: NotificationItem) {
    if (!item.readAt) markRead.mutate(item.id);
    setOpen(false);
    if (item.notificationType.startsWith('LAB_')) navigate('/laboratory');
  }

  return <div className="notification-anchor">
    <button className={`notification-trigger ${open ? 'notification-trigger-active' : ''}`} type="button" aria-label="الإشعارات" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
      <Bell size={17} strokeWidth={1.8} aria-hidden="true" />
      {(count.data?.unreadCount ?? 0) > 0 && <i>{count.data!.unreadCount > 99 ? '99+' : count.data!.unreadCount}</i>}
    </button>
    {open && <>
      <button className="notification-scrim" aria-label="إغلاق الإشعارات" onClick={() => setOpen(false)} />
      <section className="notification-popover" aria-label="مركز الإشعارات">
        <header><div><strong>الإشعارات</strong><small>{count.data?.unreadCount ?? 0} غير مقروء</small></div><button type="button" onClick={() => markAll.mutate()} disabled={!count.data?.unreadCount || markAll.isPending}>قراءة الكل</button></header>
        {items.isLoading ? <div className="notification-empty">جارٍ تحميل الإشعارات…</div> : items.data?.items.length ? <div className="notification-list">
          {items.data.items.map((item) => <button type="button" className={`notification-item ${item.readAt ? '' : 'notification-item-unread'}`} key={item.id} onClick={() => openNotification(item)}>
            <span className="notification-item-mark">{item.notificationType.startsWith('LAB_') ? <FlaskConical size={13} /> : <Inbox size={13} />}</span>
            <span className="notification-item-copy"><strong>{item.title}</strong><small>{item.body}</small><time dir="ltr">{dateText(item.createdAt)}</time></span>
            {!item.readAt && <i aria-label="غير مقروء" />}
          </button>)}
        </div> : <div className="notification-empty">لا توجد إشعارات حتى الآن.</div>}
        {items.isError && <div className="notification-empty notification-error">تعذر تحميل الإشعارات. تحقق من اتصال الخادم.</div>}
      </section>
    </>}
  </div>;
}
