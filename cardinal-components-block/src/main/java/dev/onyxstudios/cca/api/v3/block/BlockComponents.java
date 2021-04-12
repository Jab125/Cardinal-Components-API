/*
 * Cardinal-Components-API
 * Copyright (C) 2019-2021 OnyxStudios
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package dev.onyxstudios.cca.api.v3.block;

import dev.onyxstudios.cca.api.v3.component.Component;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.internal.block.InternalBlockComponentProvider;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;

/**
 * This class consists exclusively of static methods that return a {@link Component} by querying some block context.
 */
@ApiStatus.Experimental
public final class BlockComponents {
    /**
     * Retrieves a context-less {@link BlockApiLookup} for the given {@link ComponentKey}.
     *
     * <p>The component must also be exposed to the lookup by registering a provider
     * for relevant block entities.
     *
     * @param key the key denoting the component for which a block lookup will be retrieved
     * @param <C> the type of the component/API
     * @return a {@link BlockApiLookup} for retrieving instances of {@code C}
     * @see #exposeApi(ComponentKey, BlockApiLookup)
     * @see #exposeApi(ComponentKey, BlockApiLookup, BiFunction)
     */
    @ApiStatus.Experimental
    public static <C extends Component> BlockApiLookup<C, Void> getApiLookup(ComponentKey<C> key) {
        return BlockApiLookup.get(key.getId(), key.getComponentClass(), Void.class);
    }

    /**
     * Exposes an API for all block entities to which a given component is attached.
     *
     * @see #exposeApi(ComponentKey, BlockApiLookup, BiFunction)
     */
    @ApiStatus.Experimental
    public static <A, T> void exposeApi(ComponentKey<? extends A> key, BlockApiLookup<A, T> apiLookup) {
        apiLookup.registerFallback((world, pos, state, blockEntity, context) -> {
            if (blockEntity != null) {
                // yes you can cast <? extends A> to <A>
                @SuppressWarnings("unchecked") A ret = key.getNullable(blockEntity);
                return ret;
            }
            return null;
        });
    }

    /**
     * Exposes an API for all block entities to which a given component is attached.
     *
     * <p><h3>Usage Example</h3>
     * Let us pretend we have the {@code FLUID_CONTAINER} API, as defined in {@link BlockApiLookup}'s usage example.
     *
     * <pre>{@code
     * public interface FluidContainerCompound extends Component {
     *      ComponentKey<FluidContainerCompound> KEY = ComponentRegistry.register(new Identifier("mymod:fluid_container_compound"), FluidContainerCompound.class);
     *
     *      FluidContainer get(Direction side);
     * }
     * }</pre>
     *
     * <pre>{@code
     * @Override
     * public void onInitialize() {
     *     BlockComponents.exposeApi(
     *         FluidContainerCompound.KEY,
     *         MyApi.FLUID_CONTAINER,
     *         FluidContainerCompound::get
     *     );
     * }
     * }</pre>
     */
    @ApiStatus.Experimental
    public static <A, T, C extends Component> void exposeApi(ComponentKey<C> key, BlockApiLookup<A, T> apiLookup, BiFunction<? super C, ? super T, ? extends A> mapper) {
        apiLookup.registerFallback((world, pos, state, blockEntity, context) -> {
            if (blockEntity != null) {
                C ret = key.getNullable(blockEntity);
                if (ret != null) return mapper.apply(ret, context);
            }
            return null;
        });
    }

    /**
     * Exposes an API for block entities of a given type, assuming the given component is attached.
     *
     * <p>This method should be preferred to other overloads as it is more performant than the more generic alternatives.
     * If the component is not {@linkplain BlockComponentFactoryRegistry#registerFor(Class, ComponentKey, BlockEntityComponentFactory) attached}
     * to one of the {@code types}, calling {@link BlockApiLookup#find(World, BlockPos, Object)}
     * on the corresponding block will throw a {@link NoSuchElementException}.
     *
     * @see #exposeApi(ComponentKey, BlockApiLookup, BiFunction)
     */
    @ApiStatus.Experimental
    public static <A, T, C extends Component> void exposeApi(ComponentKey<C> key, BlockApiLookup<A, T> apiLookup, BiFunction<? super C, ? super T, ? extends A> mapper, BlockEntityType<?>... types) {
        apiLookup.registerForBlockEntities((blockEntity, context) -> mapper.apply(key.get(blockEntity), context), types);
    }

    /**
     * @deprecated use {@link ComponentKey#get(Object) KEY.get(blockEntity)} instead
     */
    @Deprecated
    public static <C extends Component> @Nullable C get(ComponentKey<C> key, BlockEntity blockEntity) {
        return get(key, blockEntity, null);
    }

    /**
     * @deprecated use {@link BlockApiLookup} if you need additional context, otherwise call {@link ComponentKey#get(Object) KEY.get(blockEntity)}
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval
    public static <C extends Component> @Nullable C get(ComponentKey<C> key, BlockEntity blockEntity, @Nullable Direction side) {
        World world = blockEntity.getWorld();

        if (world != null) {
            @Nullable C res = getFromBlock(key, world, blockEntity.getPos(), side, blockEntity.getCachedState());

            if (res != null) {
                return res;
            }
        }

        return key.getNullable(blockEntity);
    }

    /**
     * @deprecated use {@link BlockApiLookup} if you need additional context or if you want to query a BE-less block, otherwise call {@link ComponentKey#get(Object) KEY.get(world.getBlockEntity(pos))}
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval
    public static <C extends Component> @Nullable C get(ComponentKey<C> key, BlockView world, BlockPos pos) {
        return get(key, world, pos, null);
    }

    /**
     * @deprecated use {@link BlockApiLookup} if you need additional context or if you want to query a BE-less block, otherwise call {@link ComponentKey#get(Object) KEY.get(world.getBlockEntity(pos))}
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval
    public static <C extends Component> @Nullable C get(ComponentKey<C> key, BlockState blockState, BlockView world, BlockPos pos) {
        return get(key, blockState, world, pos, null);
    }

    /**
     * @deprecated use {@link BlockApiLookup} if you need additional context or if you want to query a BE-less block, otherwise call {@link ComponentKey#get(Object) KEY.get(world.getBlockEntity(pos))}
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval
    public static <C extends Component> @Nullable C get(ComponentKey<C> key, BlockView world, BlockPos pos, @Nullable Direction side) {
        return get(key, world.getBlockState(pos), world, pos, side);
    }

    /**
     * @deprecated use {@link BlockApiLookup} if you need additional context or if you want to query a BE-less block, otherwise call {@link ComponentKey#get(Object) KEY.get(world.getBlockEntity(pos))}
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval
    public static <C extends Component> @Nullable C get(ComponentKey<C> key, BlockState blockState, BlockView world, BlockPos pos, @Nullable Direction side) {
        @Nullable C res = getFromBlock(key, world, pos, side, blockState);

        if (res != null) {
            return res;
        }

        return getFromBlockEntity(key, world.getBlockEntity(pos));
    }

    private static <C extends Component> @Nullable C getFromBlockEntity(ComponentKey<C> key, @Nullable BlockEntity blockEntity) {
        return blockEntity != null ? key.getNullable(blockEntity) : null;
    }

    private static <C extends Component> @Nullable C getFromBlock(ComponentKey<C> key, BlockView world, BlockPos pos, @Nullable Direction side, BlockState state) {
        return ((InternalBlockComponentProvider) state.getBlock()).getComponent(key, state, world, pos, side);
    }
}
