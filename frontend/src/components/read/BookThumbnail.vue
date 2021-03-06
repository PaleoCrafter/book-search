<template>
<router-link
  class="book-thumbnail"
  :to="{ name: 'book-chapters', params: { id: this.book.id,book } }"
  @click.prevent="openBook">
  <div :id="`book-${book.id}-cover`" :data-book-id="book.id" class="book-thumbnail-cover">
    <transition name="fade-relative">
      <img :src="`/api/books/${book.id}/cover`" :alt="`'${book.title}' Cover`" v-if="hasCover">
    </transition>
  </div>
  <div class="book-thumbnail-metadata">
    <div class="book-thumbnail-title">
      {{ book.title }}
    </div>
    <div class="book-thumbnail-author">
      {{ book.author }}
    </div>
  </div>
</router-link>
</template>

<script>
export default {
  name: 'BookThumbnail',
  props: {
    book: Object,
  },
  data() {
    return {
      hasCover: false,
    };
  },
  async mounted() {
    try {
      await this.$api.get(`/books/${this.book.id}/cover`, { responseType: 'arraybuffer' });
      this.hasCover = true;
    } catch (error) {
      if (error.response && error.response.status === 404) {
        this.hasCover = false;
      } else {
        this.$handleApiError(error);
      }
    }
  },
};
</script>

<style lang="scss">
.book-thumbnail {
  display: table;
  cursor: pointer;
  position: relative;
  color: var(--base-text-color);
  width: fit-content;

  &-cover {
    position: relative;
    max-height: 19rem;
    height: 19rem;
    min-width: 10rem;
    transition: all 0.4s ease-in-out;
    z-index: 1001;

    img {
      max-height: 100%;
    }

    &:after {
      content: '';
      position: absolute;
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      left: 0;
      right: 0;
      top: 0;
      bottom: 0;
      background: rgba(255, 255, 255, 0.3);
      color: var(--base-text-color);
      transition: opacity 0.2s ease-in-out;
    }

    &.no-transition {
      transition: none;
    }
  }

  &-metadata {
    display: table-caption;
    caption-side: bottom;
  }

  &-title {
    box-sizing: border-box;
    padding: 0.25rem 0 0;
    font-weight: bold;
    max-width: 100%;
  }

  &-author {
    font-size: 0.8rem;
    color: var(--highlight-text-color);
  }

  &:hover, &:focus, &:active {
    .book-thumbnail-cover:after {
      opacity: 1;
    }

    .book-thumbnail-author {
      color: lighten($primary, 10%);
    }
  }
}
</style>
