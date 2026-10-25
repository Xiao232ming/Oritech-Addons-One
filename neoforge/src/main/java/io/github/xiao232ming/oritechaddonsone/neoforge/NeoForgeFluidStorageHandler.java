package io.github.xiao232ming.oritechaddonsone.neoforge;

import dev.architectury.fluid.FluidStack;
import dev.architectury.hooks.fluid.forge.FluidStackHooksForge;

import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;

import rearth.oritech.api.fluid.FluidApi;

/**
 * 把 Oritech 的 {@link FluidApi.FluidStorage}（跨加载器接口）包成 NeoForge 的 {@link IFluidHandler}。
 * <p>
 * 这是 Fabric 侧 {@code FabricFluidStorage} 的对应物：能力注册是加载器的事，所以适配器在加载器模块里。
 * 只翻译"Oritech 流体存储 ↔ NeoForge 流体处理器"的语义，门控、模式与机器解析都留在被包装的存储里。
 */
public record NeoForgeFluidStorageHandler(FluidApi.FluidStorage storage) implements IFluidHandler {

    @Override
    public int getTanks() {
        return Math.max(1, storage.getContent().size());
    }

    @Override
    @NotNull
    public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
        var content = storage.getContent();
        if (tank < 0 || tank >= content.size()) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;

        var stack = content.get(tank);
        return stack.isEmpty()
                ? net.neoforged.neoforge.fluids.FluidStack.EMPTY
                : FluidStackHooksForge.toForge(stack.copy());
    }

    @Override
    public int getTankCapacity(int tank) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, storage.getCapacity()));
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull net.neoforged.neoforge.fluids.FluidStack stack) {
        return true;
    }

    @Override
    public int fill(net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || resource.getAmount() <= 0) return 0;

        var inserted = storage.insert(FluidStackHooksForge.fromForge(resource), action.simulate());
        if (inserted > 0 && action.execute()) storage.update();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, inserted));
    }

    @Override
    @NotNull
    public net.neoforged.neoforge.fluids.FluidStack drain(
            net.neoforged.neoforge.fluids.FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || resource.getAmount() <= 0) {
            return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        }

        var toExtract = FluidStackHooksForge.fromForge(resource);
        var extracted = storage.extract(toExtract, action.simulate());
        if (extracted <= 0) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;
        if (action.execute()) storage.update();

        return FluidStackHooksForge.toForge(toExtract.copyWithAmount(extracted));
    }

    @Override
    @NotNull
    public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;

        var content = storage.getContent();
        if (content.isEmpty()) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;

        var first = content.getFirst();
        if (first.isEmpty()) return net.neoforged.neoforge.fluids.FluidStack.EMPTY;

        return drain(FluidStackHooksForge.toForge(first.copyWithAmount(maxDrain)), action);
    }
}
