package net.ashpapi.papioptimization.crafting;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.ashpapi.papioptimization.mixin.crafting.IngredientAccessor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.TransmuteRecipe;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.stream.Stream;

public final class PapiRecipeIndex<I extends RecipeInput, T extends Recipe<I>> {

    private static final int[] EMPTY_INDICES = new int[0];

    private static final ThreadLocal<MergeScratch> SCRATCH = ThreadLocal.withInitial(MergeScratch::new);

    private final RecipeHolder<T>[] orderedRecipes;
    private final IdentityHashMap<Item, int[]> byItem;
    private final int[] wildcards;

    @SuppressWarnings({"unchecked", "deprecation"})
    public PapiRecipeIndex(Collection<RecipeHolder<T>> ordered) {
        this.orderedRecipes = ordered.toArray(RecipeHolder[]::new);
        int count = this.orderedRecipes.length;
        if (count == 0) {
            this.byItem = new IdentityHashMap<>(0);
            this.wildcards = EMPTY_INDICES;
            return;
        }
        IdentityHashMap<Item, IntArrayList> tempBuckets = new IdentityHashMap<>();
        IntArrayList tempWildcards = new IntArrayList();
        ReferenceArrayList<Item> scratchItems = new ReferenceArrayList<>();
        ReferenceArrayList<Item> bestItems = new ReferenceArrayList<>();
        for (int idx = 0; idx < count; idx++) {
            Recipe<I> recipe = this.orderedRecipes[idx].value();
            PlacementInfo placement = recipe.placementInfo();
            if (placement.isImpossibleToPlace()) {
                tempWildcards.add(idx);
                continue;
            }
            List<Ingredient> ingredients = placement.ingredients();
            if (ingredients.isEmpty()) {
                tempWildcards.add(idx);
                continue;
            }
            bestItems.clear();
            int bestSize = Integer.MAX_VALUE;
            int n = (recipe instanceof TransmuteRecipe) ? 1 : ingredients.size();
            for (int i = 0; i < n; i++) {
                Ingredient ing = ingredients.get(i);
                if (((IngredientAccessor) (Object) ing).papi$getCustomIngredient() != null) {
                    continue;
                }
                scratchItems.clear();
                try {
                    if (ing.isEmpty()) {
                        continue;
                    }
                    ing.items().forEach(holder -> {
                        Item item = holder.value();
                        if (!scratchItems.contains(item)) {
                            scratchItems.add(item);
                        }
                    });
                } catch (IllegalStateException ignored) {
                    continue;
                }
                int size = scratchItems.size();
                if (size > 0 && size < bestSize) {
                    bestSize = size;
                    bestItems.clear();
                    bestItems.addAll(scratchItems);
                    if (size == 1) {
                        break;
                    }
                }
            }
            if (bestItems.isEmpty()) {
                tempWildcards.add(idx);
            } else {
                for (int j = 0, bSize = bestItems.size(); j < bSize; j++) {
                    Item item = bestItems.get(j);
                    tempBuckets.computeIfAbsent(item, k -> new IntArrayList()).add(idx);
                }
            }
        }
        IdentityHashMap<Item, int[]> frozen = new IdentityHashMap<>(tempBuckets.size());
        for (var entry : tempBuckets.entrySet()) {
            frozen.put(entry.getKey(), entry.getValue().toIntArray());
        }
        this.byItem = frozen;
        this.wildcards = tempWildcards.isEmpty() ? EMPTY_INDICES : tempWildcards.toIntArray();
    }

    public RecipeHolder<T>[] orderedRecipes() {
        return this.orderedRecipes;
    }

    public @Nullable RecipeHolder<T> findFirstMatch(I input, Level level) {
        return this.search(input, level, null);
    }

    public Stream<RecipeHolder<T>> findMatchingStream(I input, Level level) {
        if (this.orderedRecipes.length == 0 || input.isEmpty()) {
            return Stream.empty();
        }
        ObjectArrayList<RecipeHolder<T>> results = new ObjectArrayList<>(4);
        this.search(input, level, results);
        return results.isEmpty() ? Stream.empty() : results.stream();
    }

    private @Nullable RecipeHolder<T> search(
            I input,
            Level level,
            @Nullable ObjectArrayList<RecipeHolder<T>> out
    ) {
        RecipeHolder<T>[] recipes = this.orderedRecipes;
        if (recipes.length == 0 || input.isEmpty()) {
            return null;
        }
        int inputSize = input.size();
        MergeScratch scratch = SCRATCH.get();
        if (scratch.inUse) {
            scratch = new MergeScratch();
        }
        scratch.inUse = true;
        int distinctCount = 0;
        int listCount = 0;
        try {
            scratch.ensureItems(inputSize);
            Item[] distinct = scratch.distinctItems;
            for (int s = 0; s < inputSize; s++) {
                ItemStack stack = input.getItem(s);
                if (stack.isEmpty()) {
                    continue;
                }
                Item item = stack.getItem();
                boolean dup = false;
                for (int d = 0; d < distinctCount; d++) {
                    if (distinct[d] == item) {
                        dup = true;
                        break;
                    }
                }
                if (!dup) {
                    distinct[distinctCount++] = item;
                }
            }
            scratch.ensureLists(distinctCount + 1);
            int[][] lists = scratch.lists;
            int[] cursors = scratch.cursors;
            int[] heads = scratch.heads;
            for (int d = 0; d < distinctCount; d++) {
                int[] bucket = this.byItem.get(distinct[d]);
                if (bucket != null && bucket.length > 0) {
                    lists[listCount] = bucket;
                    cursors[listCount] = 0;
                    heads[listCount] = bucket[0];
                    listCount++;
                }
            }
            if (this.wildcards.length > 0) {
                lists[listCount] = this.wildcards;
                cursors[listCount] = 0;
                heads[listCount] = this.wildcards[0];
                listCount++;
            }
            int m = listCount;
            if (m == 0) {
                return null;
            }
            if (m == 1) {
                int[] arr = lists[0];
                for (int i = 0, len = arr.length; i < len; i++) {
                    RecipeHolder<T> holder = recipes[arr[i]];
                    if (holder.value().matches(input, level)) {
                        if (out == null) {
                            return holder;
                        }
                        out.add(holder);
                    }
                }
                return null;
            }
            if (m == 2) {
                int[] a = lists[0];
                int[] b = lists[1];
                int ia = 0;
                int ib = 0;
                int la = a.length;
                int lb = b.length;
                int last = -1;
                while (ia < la && ib < lb) {
                    int va = a[ia];
                    int vb = b[ib];
                    int idx;
                    if (va < vb) {
                        idx = va;
                        ia++;
                    } else if (vb < va) {
                        idx = vb;
                        ib++;
                    } else {
                        idx = va;
                        ia++;
                        ib++;
                    }
                    if (idx != last) {
                        last = idx;
                        RecipeHolder<T> holder = recipes[idx];
                        if (holder.value().matches(input, level)) {
                            if (out == null) {
                                return holder;
                            }
                            out.add(holder);
                        }
                    }
                }
                while (ia < la) {
                    int idx = a[ia++];
                    if (idx != last) {
                        last = idx;
                        RecipeHolder<T> holder = recipes[idx];
                        if (holder.value().matches(input, level)) {
                            if (out == null) {
                                return holder;
                            }
                            out.add(holder);
                        }
                    }
                }
                while (ib < lb) {
                    int idx = b[ib++];
                    if (idx != last) {
                        last = idx;
                        RecipeHolder<T> holder = recipes[idx];
                        if (holder.value().matches(input, level)) {
                            if (out == null) {
                                return holder;
                            }
                            out.add(holder);
                        }
                    }
                }
                return null;
            }
            int last = -1;
            while (m > 0) {
                int minVal = heads[0];
                for (int j = 1; j < m; j++) {
                    int v = heads[j];
                    if (v < minVal) {
                        minVal = v;
                    }
                }
                int j = 0;
                while (j < m) {
                    if (heads[j] == minVal) {
                        int nextPos = cursors[j] + 1;
                        int[] arr = lists[j];
                        if (nextPos < arr.length) {
                            cursors[j] = nextPos;
                            heads[j] = arr[nextPos];
                            j++;
                        } else {
                            int tail = --m;
                            if (j < tail) {
                                lists[j] = lists[tail];
                                cursors[j] = cursors[tail];
                                heads[j] = heads[tail];
                            }
                        }
                    } else {
                        j++;
                    }
                }
                if (minVal != last) {
                    last = minVal;
                    RecipeHolder<T> holder = recipes[minVal];
                    if (holder.value().matches(input, level)) {
                        if (out == null) {
                            return holder;
                        }
                        out.add(holder);
                    }
                }
            }
            return null;
        } finally {
            if (distinctCount > 0) {
                Arrays.fill(scratch.distinctItems, 0, distinctCount, null);
            }
            if (listCount > 0) {
                Arrays.fill(scratch.lists, 0, listCount, null);
            }
            scratch.inUse = false;
        }
    }

    private static final class MergeScratch {
        boolean inUse;
        Item[] distinctItems = new Item[16];
        int[][] lists = new int[16][];
        int[] cursors = new int[16];
        int[] heads = new int[16];

        void ensureItems(int cap) {
            if (this.distinctItems.length < cap) {
                this.distinctItems = new Item[cap];
            }
        }

        void ensureLists(int cap) {
            if (this.lists.length < cap) {
                int n = Math.max(cap, this.lists.length << 1);
                this.lists = new int[n][];
                this.cursors = new int[n];
                this.heads = new int[n];
            }
        }
    }
}
