<template>
<div :class="{ 'search-result': true, 'search-result-with-siblings': showSiblings }"
     @click="toggleSiblings">
  <h2 v-if="displayMetadata">
    <router-link
      :to="{
          name: 'search-preview',
          params: { id: result.chapter.id },
          query: { ...$route.query, q: query, position: result.mainParagraph.position }
        }"
      @click.native="$event.stopImmediatePropagation()">
      {{ result.book.title }} - {{ result.chapter.title }}
    </router-link>
    <Button
      v-if="citation !== null"
      class="search-result-cite"
      slim
      @click="cite"
      @click.native="$event.stopImmediatePropagation()"
    >
      Cite
    </Button>
  </h2>
  <BookText
    :book-title="result.book.title"
    :chapter-title="result.chapter.title"
    :citation="citation"
  >
    <BidirectionalExpandable :expanded="showSiblings" :visible-height="24">
      <template slot="start">
      <p :class="paragraph.classes" v-html="paragraph.text"
         v-for="paragraph in result.prevParagraphs" :key="paragraph.position">
      </p>
      </template>
      <p :class="result.mainParagraph.classes" v-html="result.mainParagraph.text"></p>
      <template slot="end">
      <p :class="paragraph.classes" v-html="paragraph.text"
         v-for="paragraph in result.nextParagraphs" :key="paragraph.position">
      </p>
      <router-link
        :to="{
          name: 'search-preview',
          params: { id: result.chapter.id },
          query: { ...$route.query, q: query, position: result.mainParagraph.position }
        }"
        @click.native="$event.stopImmediatePropagation()">
        Read from here
      </router-link>
      </template>
    </BidirectionalExpandable>
  </BookText>
</div>
</template>

<script>
import BidirectionalExpandable from '@/components/BidirectionalExpandable.vue';
import BookText from '@/components/BookText.vue';
import { buildCitation, copyText } from '@/utils';
import Button from '@/components/Button.vue';

export default {
  name: 'search-result',
  components: {
    Button,
    BookText,
    BidirectionalExpandable,
  },
  props: {
    query: String,
    result: Object,
    displayMetadata: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      showSiblings: false,
    };
  },
  computed: {
    citation() {
      return buildCitation(this.result.book, this.result.chapter);
    },
  },
  methods: {
    toggleSiblings() {
      if (window.getSelection().type !== 'Range') {
        this.showSiblings = !this.showSiblings;
      }
    },
    cite() {
      copyText(this.citation);
      this.$notifications.success('A citation for this chapter has been copied to your clipboard!');
    },
  },
};
</script>

<style lang="scss">
.search-result {
  box-sizing: border-box;
  background: var(--section-bg);
  border-radius: 3px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  box-shadow: 0 0.75rem 1rem rgba(0, 0, 0, 0.1);
  padding: 1rem;
  margin: 1rem 0;
  position: relative;
  cursor: pointer;

  h2 {
    display: flex;
    align-items: center;
    margin: 0;

    .search-result-cite {
      margin-left: auto;
      height: auto;
      padding: 0.5rem;
      font-size: 0.9rem;
    }
  }

  & > .book-text {
    .chapterText {
      text-indent: 0;
    }

    .bidirectional-expandable-start p:last-of-type {
      margin-bottom: 0;
    }

    .bidirectional-expandable-end p:first-of-type {
      margin-top: 0;
    }
  }
}
</style>
