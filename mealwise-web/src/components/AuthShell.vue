<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    eyebrow?: string
  }>(),
  {
    subtitle: '',
    eyebrow: 'MEALWISE'
  }
)
</script>

<template>
  <div class="auth-shell">
    <aside class="auth-brand">
      <div class="auth-brand__orb auth-brand__orb--one" aria-hidden="true"></div>
      <div class="auth-brand__orb auth-brand__orb--two" aria-hidden="true"></div>

      <RouterLink to="/login" class="auth-logo">
        <span class="auth-logo__mark" aria-hidden="true">
          <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect width="32" height="32" rx="10" fill="rgba(255, 255, 255, 0.16)" />
            <path
              d="M23 8.6c-6.5.4-10.8 4-10.8 9 0 1.6.5 2.9 1.4 3.9 4.9-.7 9.4-4.8 9.4-12.9Z"
              fill="#ffffff"
              fill-opacity="0.95"
            />
            <path
              d="M10 24c1.4-4.2 3.9-6.7 7.6-8.4"
              stroke="#ffffff"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-opacity="0.9"
            />
          </svg>
        </span>
        <span class="auth-logo__text">Mealwise</span>
      </RouterLink>

      <div class="auth-brand__content">
        <p class="auth-brand__eyebrow">EAT WITH CLARITY</p>
        <h1 class="auth-brand__title">每一餐，<br />都吃得明明白白。</h1>
        <p class="auth-brand__desc">
          记录吃了什么、吃了多少，自动算出热量与三大营养素；再按同类食物，找到更适合自己的替换选择。
        </p>

        <ul class="auth-brand__features">
          <li>
            <span class="feature-check" aria-hidden="true">✓</span>
            <span>食物库记录每 100g 的完整营养</span>
          </li>
          <li>
            <span class="feature-check" aria-hidden="true">✓</span>
            <span>一餐合计实时更新，克数随改随算</span>
          </li>
          <li>
            <span class="feature-check" aria-hidden="true">✓</span>
            <span>同类食物替换，热量增减一目了然</span>
          </li>
        </ul>
      </div>

      <p class="auth-brand__footer">Mealwise · 你的日常营养记录本</p>
    </aside>

    <section class="auth-panel">
      <div class="auth-panel__inner">
        <div class="auth-mobile-brand">
          <span class="auth-mobile-brand__mark" aria-hidden="true">M</span>
          <span>Mealwise</span>
        </div>

        <div class="auth-card">
          <p class="auth-card__eyebrow">{{ eyebrow }}</p>
          <h2 class="auth-card__title">{{ title }}</h2>
          <p v-if="subtitle" class="auth-card__subtitle">{{ subtitle }}</p>

          <slot />

          <div class="auth-card__footer">
            <slot name="footer" />
          </div>
        </div>

        <p class="auth-panel__note">你的数据只用于个人营养记录</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-shell {
  width: 100%;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  background: #fbfdfb;
}

/* ---------------------------- 左侧品牌区 ---------------------------- */

.auth-brand {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 44px 56px;
  color: #ffffff;
  background:
    radial-gradient(900px 600px at 80% 10%, rgba(111, 213, 163, 0.28), transparent 60%),
    linear-gradient(150deg, #2f9d6b 0%, #1f6c49 58%, #143f2c 100%);
}

.auth-brand__orb {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.16);
  pointer-events: none;
}

.auth-brand__orb--one {
  width: 520px;
  height: 520px;
  right: -220px;
  top: -180px;
  background: rgba(255, 255, 255, 0.05);
}

.auth-brand__orb--two {
  width: 280px;
  height: 280px;
  right: 40px;
  bottom: -120px;
  background: rgba(255, 255, 255, 0.04);
}

.auth-logo {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  gap: 11px;
  color: #ffffff;
  width: fit-content;
}

.auth-logo__mark {
  width: 40px;
  height: 40px;
}

.auth-logo__mark svg {
  width: 100%;
  height: 100%;
  display: block;
}

.auth-logo__text {
  font-size: 21px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.auth-brand__content {
  position: relative;
  z-index: 1;
  margin-top: auto;
  margin-bottom: auto;
  max-width: 500px;
  padding: 48px 0;
}

.auth-brand__eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.22em;
  color: rgba(255, 255, 255, 0.68);
  margin-bottom: 18px;
}

.auth-brand__title {
  font-size: clamp(34px, 4vw, 52px);
  line-height: 1.18;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.auth-brand__desc {
  margin-top: 20px;
  max-width: 420px;
  font-size: 15px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.78);
}

.auth-brand__features {
  list-style: none;
  margin: 34px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth-brand__features li {
  display: flex;
  align-items: center;
  gap: 11px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.88);
}

.feature-check {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 800;
  color: #1f6c49;
  background: rgba(255, 255, 255, 0.9);
  flex-shrink: 0;
}

.auth-brand__footer {
  position: relative;
  z-index: 1;
  font-size: 12px;
  letter-spacing: 0.04em;
  color: rgba(255, 255, 255, 0.55);
}

/* ---------------------------- 右侧表单区 ---------------------------- */

.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
  background:
    radial-gradient(600px 380px at 90% 0%, rgba(240, 162, 58, 0.08), transparent 60%),
    #fbfdfb;
}

.auth-panel__inner {
  width: 100%;
  max-width: 430px;
}

.auth-mobile-brand {
  display: none;
  align-items: center;
  gap: 10px;
  margin-bottom: 26px;
  font-size: 19px;
  font-weight: 800;
  color: var(--mw-ink);
}

.auth-mobile-brand__mark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-size: 15px;
  background: linear-gradient(135deg, #43b985, #237b53);
}

.auth-card {
  background: #ffffff;
  border: 1px solid rgba(227, 235, 230, 0.9);
  border-radius: 24px;
  padding: 38px 36px 32px;
  box-shadow: 0 30px 70px -38px rgba(24, 62, 44, 0.4), 0 2px 8px -4px rgba(24, 62, 44, 0.08);
}

.auth-card__eyebrow {
  font-size: 10.5px;
  font-weight: 700;
  letter-spacing: 0.18em;
  color: var(--mw-primary);
  margin-bottom: 10px;
}

.auth-card__title {
  font-size: 27px;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--mw-ink);
}

.auth-card__subtitle {
  margin-top: 9px;
  font-size: 13.5px;
  line-height: 1.65;
  color: var(--mw-muted);
}

.auth-card__footer {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--mw-line);
  font-size: 13px;
  color: var(--mw-muted);
  text-align: center;
}

.auth-card__footer :deep(a) {
  font-weight: 700;
  color: var(--mw-primary);
}

.auth-panel__note {
  margin-top: 18px;
  text-align: center;
  font-size: 11.5px;
  color: var(--mw-faint);
}

@media (max-width: 980px) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .auth-brand {
    display: none;
  }

  .auth-panel {
    min-height: 100vh;
    padding: 32px 20px;
    align-items: flex-start;
  }

  .auth-panel__inner {
    margin-top: 6vh;
  }

  .auth-mobile-brand {
    display: flex;
  }
}

@media (max-width: 520px) {
  .auth-card {
    padding: 28px 22px 24px;
    border-radius: 20px;
  }

  .auth-card__title {
    font-size: 24px;
  }

  .auth-panel {
    padding: 24px 14px;
  }

  .auth-panel__inner {
    margin-top: 3vh;
  }
}
</style>
