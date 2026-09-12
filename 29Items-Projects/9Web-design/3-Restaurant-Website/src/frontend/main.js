import { MenuPreview } from './components/MenuPreview.js';
import { ReservationForm } from './components/ReservationForm.js';
import { trackEvent } from './analytics.js';
import { escapeHtml } from './html.js';
import { featuredMenu } from './data/featuredMenu.js';

const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function initNavigation() {
  const toggle = document.querySelector('[data-nav-toggle]');
  const menu = document.querySelector('[data-nav-menu]');

  if (!toggle || !menu) return;

  toggle.addEventListener('click', () => {
    const isOpen = toggle.getAttribute('aria-expanded') === 'true';
    toggle.setAttribute('aria-expanded', String(!isOpen));
    menu.classList.toggle('hidden', isOpen);
    menu.classList.toggle('absolute', !isOpen);
    menu.classList.toggle('left-4', !isOpen);
    menu.classList.toggle('right-4', !isOpen);
    menu.classList.toggle('top-16', !isOpen);
    menu.classList.toggle('grid', !isOpen);
    menu.classList.toggle('rounded-lg', !isOpen);
    menu.classList.toggle('bg-stone-950', !isOpen);
    menu.classList.toggle('p-4', !isOpen);
  });
}

function initAnimations() {
  if (prefersReducedMotion || !window.gsap) return;

  window.gsap.registerPlugin(window.ScrollTrigger);
  window.gsap.from('h1, section h2', {
    y: 28,
    opacity: 0,
    duration: 0.8,
    stagger: 0.08,
    ease: 'power3.out',
    scrollTrigger: {
      trigger: 'main',
      start: 'top 75%'
    }
  });
}

function initGallery() {
  if (window.Swiper) {
    new window.Swiper('[data-gallery]', {
      slidesPerView: 1.1,
      spaceBetween: 16,
      navigation: {
        prevEl: '.gallery-prev',
        nextEl: '.gallery-next'
      },
      breakpoints: {
        768: { slidesPerView: 2.2 },
        1024: { slidesPerView: 3 }
      }
    });
  }

  const lightbox = document.querySelector('[data-lightbox]');
  const image = document.querySelector('[data-lightbox-image]');
  const close = document.querySelector('[data-lightbox-close]');

  document.querySelectorAll('[data-lightbox-src]').forEach((trigger) => {
    trigger.addEventListener('click', () => {
      image.src = trigger.dataset.lightboxSrc;
      lightbox.classList.remove('hidden');
      lightbox.classList.add('flex');
      close.focus();
      trackEvent('gallery_open', { src: trigger.dataset.lightboxSrc });
    });
  });

  const closeLightbox = () => {
    lightbox.classList.add('hidden');
    lightbox.classList.remove('flex');
    image.removeAttribute('src');
  };

  close?.addEventListener('click', closeLightbox);
  lightbox?.addEventListener('click', (event) => {
    if (event.target === lightbox) closeLightbox();
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !lightbox.classList.contains('hidden')) closeLightbox();
  });
}

function initFeaturedMenuCarousel() {
  const slides = document.querySelector('[data-featured-menu-slides]');
  if (!slides) return;

  slides.innerHTML = featuredMenu
    .map(
      (item) => `
        <article class="swiper-slide rounded-lg border border-stone-200 bg-white p-5 shadow-sm">
          <p class="text-xs font-semibold uppercase tracking-wide text-amber-700">${escapeHtml(item.category)}</p>
          <h4 class="mt-2 text-lg font-bold">${escapeHtml(item.name)}</h4>
          <p class="mt-3 text-sm leading-6 text-stone-600">${escapeHtml(item.description)}</p>
          <p class="mt-4 font-semibold text-stone-950">${escapeHtml(item.price)}</p>
        </article>
      `
    )
    .join('');

  if (window.Swiper) {
    new window.Swiper('[data-featured-menu-carousel]', {
      slidesPerView: 1.1,
      spaceBetween: 16,
      breakpoints: {
        768: { slidesPerView: 2.2 },
        1024: { slidesPerView: 3 }
      }
    });
  }
}

function initIcons() {
  if (window.lucide) {
    window.lucide.createIcons();
  }
}

initNavigation();
initIcons();
initGallery();
initFeaturedMenuCarousel();
initAnimations();

new MenuPreview({
  mount: document.querySelector('[data-menu-preview]'),
  filters: document.querySelector('[data-menu-filters]'),
  source: '/data/menu.json',
  onFilterChange: (category) => trackEvent('menu_filter', { category })
}).init();

new ReservationForm({
  form: document.querySelector('[data-reservation-form]'),
  endpoint: '/api/reservations',
  onSuccess: () => trackEvent('reservation_request_success'),
  onError: (reason) => trackEvent('reservation_request_error', { reason })
}).init();
