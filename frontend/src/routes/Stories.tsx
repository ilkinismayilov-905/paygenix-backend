import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { t } from '../i18n'
import { Logo } from './Logo'

type Story = components['schemas']['StoryResponse']
type Group = components['schemas']['StoryGroupResponse']

const IMAGE_SECONDS = 5

/** Promo stories row on the dashboard (GET /api/stories/feed). Renders nothing while loading, on error or with no stories. */
export function StoriesRow() {
  const [group, setGroup] = useState<Group | null>(null)
  const [open, setOpen] = useState(false)
  useEffect(() => {
    let live = true
    void api.GET('/api/stories/feed')
      .then(({ data, response }) => {
        const g = data?.data?.groups?.[0]
        if (live && response.ok && g?.stories?.length) setGroup(g)
      })
      .catch(() => {}) // stories are extra: the dashboard works without them
    return () => { live = false }
  }, [])
  const markSeen = useCallback((id: number) =>
    setGroup((g) => g && { ...g, stories: g.stories?.map((s) => (s.storyId === id ? { ...s, viewed: true } : s)) }), [])
  if (!group) return null
  const stories = group.stories ?? []
  const unseen = stories.some((s) => !s.viewed)
  return (
    <>
      <div className="stories-row">
        <button type="button" className={`story-ring${unseen ? ' story-ring-new' : ''}`} onClick={() => setOpen(true)}
          aria-label={t(unseen ? 'stories.openNew' : 'stories.open', { name: group.username ?? '' })}>
          <span className="story-avatar"><Logo size={40} /></span>
          <span className="story-name" aria-hidden="true">{group.username}</span>
        </button>
      </div>
      {open && (
        <StoryViewer name={group.username ?? ''} stories={stories}
          onSeen={markSeen}
          onClose={() => setOpen(false)} />
      )}
    </>
  )
}

/** Full-screen viewer: images stay 5 s, videos play to the end; tap the sides or use the buttons/arrow keys to move. */
function StoryViewer({ name, stories, onSeen, onClose }:
  { name: string; stories: Story[]; onSeen: (id: number) => void; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  // start at the first unseen story, like other story viewers
  const [index, setIndex] = useState(() => Math.max(0, stories.findIndex((s) => !s.viewed)))
  const [failed, setFailed] = useState(false)
  const story = stories[index]!

  useEffect(() => {
    const d = ref.current
    if (d && !d.open) d.showModal()
  }, [])

  const next = useCallback(() => {
    setFailed(false)
    if (index + 1 < stories.length) setIndex(index + 1)
    else ref.current?.close()
  }, [index, stories.length])
  const prev = () => { setFailed(false); setIndex((i) => Math.max(0, i - 1)) }

  // record the view once it is on screen; failures are ignored (it shows as unseen next time)
  useEffect(() => {
    if (!story.storyId || story.viewed) return
    const id = story.storyId
    void api.POST('/api/stories/{storyId}/view', { params: { path: { storyId: id } } })
      .then(({ response }) => { if (response.ok) onSeen(id) }).catch(() => {})
  }, [story.storyId, story.viewed, onSeen])

  useEffect(() => {
    if (story.mediaType === 'VIDEO' || failed) return
    const timer = setTimeout(next, IMAGE_SECONDS * 1000)
    return () => clearTimeout(timer)
  }, [story, failed, next])

  return (
    <dialog ref={ref} className="story-viewer" aria-label={t('stories.viewer', { name, n: index + 1, total: stories.length })}
      onClose={onClose}
      onKeyDown={(e) => { if (e.key === 'ArrowRight') next(); if (e.key === 'ArrowLeft') prev() }}>
      <div className="story-progress" aria-hidden="true">
        {stories.map((s, i) => (
          <span key={s.storyId} className={i < index ? 'done' : i === index ? 'current' : ''}
            style={i === index && story.mediaType !== 'VIDEO' ? { animationDuration: `${IMAGE_SECONDS}s` } : undefined} />
        ))}
      </div>
      <div className="story-top">
        <span className="story-title"><Logo size={28} /> {name}</span>
        <button type="button" className="story-close" onClick={() => ref.current?.close()} aria-label={t('stories.close')}>×</button>
      </div>
      <div className="story-media">
        {failed ? <p role="alert">{t('stories.failed')}</p>
          : story.mediaType === 'VIDEO'
            ? <video key={story.storyId} src={story.mediaUrl} autoPlay playsInline muted onEnded={next} onError={() => setFailed(true)} />
            : <img key={story.storyId} src={story.mediaUrl} alt={t('stories.alt', { name })} onError={() => setFailed(true)} />}
      </div>
      <button type="button" className="story-tap story-tap-prev" onClick={prev} disabled={index === 0}>
        <span className="sr-only">{t('stories.prev')}</span>
      </button>
      <button type="button" className="story-tap story-tap-next" onClick={next}>
        <span className="sr-only">{t(index + 1 < stories.length ? 'stories.next' : 'stories.end')}</span>
      </button>
    </dialog>
  )
}
