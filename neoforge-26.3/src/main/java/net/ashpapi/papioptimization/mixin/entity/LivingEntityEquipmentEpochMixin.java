package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = LivingEntity.class, priority = 1500)
public abstract class LivingEntityEquipmentEpochMixin {

    @Unique
    private static final EquipmentSlot[] PAPI$SLOTS = EquipmentSlot.values();

    @Shadow public abstract ItemStack getItemBySlot(EquipmentSlot slot);
    @Shadow protected abstract boolean equipmentHasChanged(ItemStack oldItem, ItemStack newItem);

    @WrapMethod(method = "collectEquipmentChanges")
    private @Nullable Map<EquipmentSlot, ItemStack> papi$fastCollectEquipmentChanges(
            Map<EquipmentSlot, ItemStack> lastEquipmentItems,
            Operation<@Nullable Map<EquipmentSlot, ItemStack>> original
    ) {
        for (int i = 0, len = PAPI$SLOTS.length; i < len; i++) {
            EquipmentSlot slot = PAPI$SLOTS[i];
            ItemStack prev = lastEquipmentItems.get(slot);
            ItemStack cur = this.getItemBySlot(slot);
            if (prev != cur && this.equipmentHasChanged(prev, cur)) {
                return original.call(lastEquipmentItems);
            }
        }
        return null;
    }
}
