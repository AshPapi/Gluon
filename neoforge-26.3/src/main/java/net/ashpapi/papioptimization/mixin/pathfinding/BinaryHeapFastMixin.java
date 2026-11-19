package net.ashpapi.papioptimization.mixin.pathfinding;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * PapiOptimization BinaryHeap Optimization:
 * Localizes array references and sizes into CPU registers, inlining bit shifts
 * and eliminating redundant heap field lookups during pathfinding node relaxation.
 * 100% exact priority queue parity.
 */
@Mixin(value = BinaryHeap.class, priority = 1500)
public abstract class BinaryHeapFastMixin {

    @Shadow private Node[] heap;
    @Shadow private int size;

    /**
     * @author AshPapi
     * @reason High-performance loop with local array and size caching for binary min-heap
     */
    @WrapMethod(method = "upHeap")
    private void papi$fastUpHeap(int index, Operation<Void> original) {
        Node[] h = this.heap;
        Node node = h[index];
        float f = node.f;

        while (index > 0) {
            int parent = (index - 1) >>> 1;
            Node parentNode = h[parent];
            if (f >= parentNode.f) {
                break;
            }
            h[index] = parentNode;
            parentNode.heapIdx = index;
            index = parent;
        }

        h[index] = node;
        node.heapIdx = index;
    }

    /**
     * @author AshPapi
     * @reason High-performance loop with local array and size caching for binary min-heap
     */
    @WrapMethod(method = "downHeap")
    private void papi$fastDownHeap(int index, Operation<Void> original) {
        Node[] h = this.heap;
        int s = this.size;
        Node node = h[index];
        float f = node.f;

        while (true) {
            int left = (index << 1) + 1;
            int right = left + 1;
            if (left >= s) {
                break;
            }

            Node leftNode = h[left];
            float leftF = leftNode.f;
            Node rightNode;
            float rightF;

            if (right >= s) {
                rightNode = null;
                rightF = Float.POSITIVE_INFINITY;
            } else {
                rightNode = h[right];
                rightF = rightNode.f;
            }

            if (leftF < rightF) {
                if (leftF >= f) {
                    break;
                }
                h[index] = leftNode;
                leftNode.heapIdx = index;
                index = left;
            } else {
                if (rightF >= f) {
                    break;
                }
                h[index] = rightNode;
                rightNode.heapIdx = index;
                index = right;
            }
        }

        h[index] = node;
        node.heapIdx = index;
    }
}
