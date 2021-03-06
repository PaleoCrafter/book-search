<template>
<div :class="classes">
  <div class="expandable-header" @click="toggle">
    <slot name="header"></slot>
    <ChevronDownIcon class="expandable-toggle-button"></ChevronDownIcon>
  </div>
  <collapse-transition>
    <div class="expandable-content" v-if="expanded">
      <slot></slot>
    </div>
  </collapse-transition>
  <slot name="footer"></slot>
</div>
</template>

<script>
import CollapseTransition from 'vue2-transitions/src/Collapse/CollapseTransition.vue';
import { ChevronDownIcon } from 'vue-feather-icons';

export default {
  name: 'Expandable',
  components: { ChevronDownIcon, CollapseTransition },
  props: {
    startExpanded: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      expanded: this.startExpanded,
    };
  },
  computed: {
    classes() {
      return {
        expandable: true,
        'expandable-expanded': this.expanded,
      };
    },
  },
  methods: {
    toggle() {
      this.expanded = !this.expanded;
      this.$emit(this.expanded ? 'expanded' : 'closed');
    },
  },
};
</script>

<style lang="scss">
.expandable {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);

  &:last-child {
    border-bottom: none;
  }

  &-header {
    display: flex;
    flex-direction: row;
    padding: 1rem 0.5rem;
    align-items: center;
    position: relative;

    .checkbox {
      margin: 0;
    }

    &:hover {
      cursor: pointer;
    }
  }

  &-toggle-button {
    margin-left: auto;
    transition: transform 0.2s ease-in-out;
    transform-origin: 50% 50%;
  }

  &-expanded {
    & > .expandable-header .expandable-toggle-button {
      transform: rotate(-180deg);
    }
  }

  &-content {
    padding: 0 0.5rem;
  }
}
</style>
