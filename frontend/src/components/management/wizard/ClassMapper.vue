<template>
<div class="class-mapper">
  <h2>CSS Classes</h2>
  <div class="class-mapper-list">
    <Expandable v-for="cls in classes" :key="cls.name">
      <template slot="header">
      <label :for="`class-mapping-${cls.name}`">{{ cls.name }} ({{ cls.occurrences }} Occurrences):</label>
      <select :id="`class-mapping-${cls.name}`" v-model="cls.mapping">
        <option value="no-selection" disabled selected>Choose style</option>
        <optgroup :label="group" v-for="(mappings, group) in availableMappings" :key="group">
          <option :value="mapping.id" v-for="mapping in mappings" :key="mapping.id">
            {{ mapping.description }}
          </option>
        </optgroup>
      </select>
      </template>
      <ClassPreview :cls="cls">
      </ClassPreview>
    </Expandable>
  </div>
  <div class="button-bar">
    <Button slim @click="$router.back()" :disabled="updating">Back</Button>
    <Button slim @click="index" :loading="updating" :disabled="updating">Next</Button>
  </div>
</div>
</template>

<script>
import Button from '@/components/Button.vue';
import ClassPreview from '@/components/management/wizard/ClassPreview.vue';
import Expandable from '@/components/Expandable.vue';

export default {
  name: 'ClassMapper',
  components: { Expandable, ClassPreview, Button },
  async mounted() {
    this.updating = true;
    const { id } = this.$route.params;
    if (!id) {
      this.$router.replace({ name: 'book-upload' });
      return;
    }

    const { data: { classes, mappings } } = await this.$api.get(`/book/${id}/available-classes`);

    this.bookId = id;
    this.classes = classes.map(cls => ({ ...cls, mapping: 'no-selection' }));
    this.availableMappings = mappings;
    this.updating = false;
  },
  data() {
    return {
      bookId: '',
      classes: [],
      updating: false,
      availableMappings: {},
    };
  },
  methods: {
    async index() {
      this.updating = true;
      try {
        await this.$api.put(
          `/book/${this.bookId}/class-mappings`,
          this.classes.reduce(
            (acc, cls) => ({ ...acc, [cls.name]: cls.mapping }),
            {},
          ),
          {
            headers: {
              'Content-Type': 'application/json',
            },
          },
        );
        await this.$api.put(`/book/${this.bookId}/index`);
        this.updating = false;
      } catch (error) {
        this.updating = false;
      }
    },
  },
};
</script>

<style lang="scss">
.class-mapper {
  display: flex;
  flex-direction: column;
  align-items: stretch;

  &-list {
    overflow-y: auto;
    flex-grow: 1;
    max-height: 100%;

    select {
      margin-left: auto;
      border: 1px solid rgba(0, 0, 0, 0.1);
      padding: 0.5rem;
      border-radius: 3px;
      background: none;
    }
  }
}
</style>