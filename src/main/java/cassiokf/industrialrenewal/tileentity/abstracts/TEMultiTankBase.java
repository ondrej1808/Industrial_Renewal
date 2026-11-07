package cassiokf.industrialrenewal.tileentity.abstracts;

import cassiokf.industrialrenewal.util.MachinesUtils;
import cassiokf.industrialrenewal.util.Utils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base for vertically stackable multiblocks (tanks, storage racks, battery banks).
 * Masters stacked exactly 3 blocks apart form one "stack"; only the BOTTOM member holds the shared
 * contents and is resized to {@code n * capacity}.
 * <p>
 * Patched: the stack is now always re-resolved as a whole ({@link #relinkStack()}), so every member gets
 * correct {@code bottomTE/topTE/isBottom/isTop} (previously intermediate members kept stale pointers to
 * destroyed TEs, which voided inserted items). Contents of members that stop being the bottom are moved
 * into the new bottom, and on break the contents are redistributed instead of being dumped/lost.
 */
public abstract class TEMultiTankBase<T extends TEMultiTankBase> extends TileEntityMultiBlockBase<T>
{
    /** vertical distance between masters of two stacked multiblocks */
    protected static final int STACK_SPACING = 3;

    protected final List<T> machines = new ArrayList<>();
    public T bottomTE = (T) this;
    public T topTE = (T) this;
    private boolean isBottom = false;
    private boolean isTop = false;
    /** client only: a data packet arrived, re-resolve the stack on the next tick */
    private boolean relinkPending = false;
    /** client only: last stack size applied via setSize, to avoid resetting client GUI state each packet */
    private int lastClientSize = -1;

    @Override
    public List<BlockPos> getListOfBlockPositions(BlockPos centerPosition)
    {
        return MachinesUtils.getBlocksIn3x3x3Centered(centerPosition);
    }

    /**
     * Ticks the TE and, on the client, applies a pending stack relink requested by a data packet.
     *
     * @return nothing
     */
    @Override
    public void update()
    {
        super.update();
        if (relinkPending && world != null && world.isRemote && isMaster())
        {
            relinkPending = false;
            relinkStack();
        }
    }

    @Override
    public void onFirstTick()
    {
        if (isMaster())
        {
            relinkStack();
        }
    }

    /**
     * Returns the valid stacked master TE at the given offset (in stack steps) from this one.
     *
     * @param from  member to start from
     * @param steps +1 = next above, -1 = next below
     * @return neighbour master TE, or {@code null} if there is none / it is invalid
     */
    @SuppressWarnings("unchecked")
    private T neighbour(TEMultiTankBase<?> from, int steps)
    {
        TileEntity te = world.getTileEntity(from.pos.up(STACK_SPACING * steps));
        if (instanceOf(te) && !te.isInvalid() && ((TEMultiTankBase<?>) te).isMaster()) return (T) te;
        return null;
    }

    /**
     * Collects all members of the stack this TE belongs to, bottom first. Invalid (broken) TEs are skipped
     * and therefore split the stack.
     *
     * @return ordered member list (never empty: contains at least this TE)
     */
    @SuppressWarnings("unchecked")
    public List<T> collectStack()
    {
        T bottom = (T) this;
        T below;
        while ((below = neighbour(bottom, -1)) != null) bottom = below;
        List<T> list = new ArrayList<>();
        T current = bottom;
        while (current != null)
        {
            list.add(current);
            current = neighbour(current, 1);
        }
        return list;
    }

    /**
     * Re-resolves the whole stack this TE is part of: sets bottom/top flags and pointers on every member,
     * resizes the bottom and (server side) moves contents of non-bottom members into the bottom.
     *
     * @return nothing; syncs all members to clients (server side)
     */
    public void relinkStack()
    {
        if (world == null || isInvalid()) return;
        List<T> stack = collectStack();
        T bottom = stack.get(0);
        T top = stack.get(stack.size() - 1);
        for (int i = 0; i < stack.size(); i++)
        {
            TEMultiTankBase<T> member = stack.get(i);
            member.isBottom = i == 0;
            member.isTop = i == stack.size() - 1;
            member.bottomTE = bottom;
            member.topTE = top;
        }
        bottom.machines.clear();
        bottom.machines.addAll(stack);
        top.machines.clear();
        top.machines.addAll(stack);

        if (!world.isRemote)
        {
            bottom.setSize(stack.size());
            // contents that ended up in a non-bottom member (old saves, merged stacks) go to the bottom
            for (int i = 1; i < stack.size(); i++)
            {
                T member = stack.get(i);
                List<ItemStack> leftovers = bottom.insertContents(member.extractAllContents());
                for (ItemStack stack1 : leftovers) Utils.spawnItemStack(world, member.getPos(), stack1);
            }
            for (T member : stack) member.sync();
        }
        else if (((TEMultiTankBase<?>) bottom).lastClientSize != stack.size())
        {
            ((TEMultiTankBase<?>) bottom).lastClientSize = stack.size();
            bottom.setSize(stack.size());
        }
    }

    @Override
    public void onMasterBreak()
    {
        if (world.isRemote) return;
        // take everything out of the shared storage before the stack is split/resized, so nothing is dropped
        // by setSize() and nothing is left in a destroyed TE
        List<ItemStack> pool = new ArrayList<>();
        T oldBottom = getBottomTE();
        if (oldBottom != null && oldBottom != this && !oldBottom.isInvalid())
            pool.addAll(oldBottom.extractAllContents());
        pool.addAll(this.extractAllContents());

        T below = neighbour(this, -1);
        T above = neighbour(this, 1);
        this.invalidate();

        if (below != null) below.relinkStack();
        if (above != null) above.relinkStack();

        // refill: lower part first (that is where the stored items physically were), then the upper part
        if (below != null) pool = below.getBottomTE().insertContents(pool);
        if (above != null) pool = above.getBottomTE().insertContents(pool);
        for (ItemStack stack : pool) Utils.spawnItemStack(world, pos, stack);
        if (below != null) below.getBottomTE().sync();
        if (above != null) above.getBottomTE().sync();
    }

    /**
     * Removes and returns all item contents stored in THIS TE's own storage (not the shared one).
     * Default: no item contents.
     *
     * @return removed stacks (each at most its max stack size); empty list if nothing
     */
    public List<ItemStack> extractAllContents()
    {
        return Collections.emptyList();
    }

    /**
     * Inserts stacks into THIS TE's own storage as far as they fit.
     * Default: accepts nothing.
     *
     * @param stacks stacks to insert (not modified)
     * @return stacks that did not fit
     */
    public List<ItemStack> insertContents(List<ItemStack> stacks)
    {
        return stacks;
    }

    /**
     * Legacy entry point (kept for subclasses): re-resolves the stack.
     *
     * @param top ignored
     */
    public void checkSize(T top)
    {
        relinkStack();
    }

    public abstract void setSize(int i);

    /**
     * Legacy entry point (kept for subclasses, e.g. battery bank after adding a battery): re-resolves the stack.
     */
    public void reachTop()
    {
        relinkStack();
    }

    public T getBottomTE()
    {
        return bottomTE;
    }

    public T getTopTE()
    {
        return topTE;
    }

    /**
     * Legacy setter; the flag is now derived by {@link #relinkStack()}, which this triggers.
     *
     * @param value ignored
     */
    public void setTop(boolean value)
    {
        relinkStack();
    }

    public boolean isBottom()
    {
        return isBottom;
    }

    /**
     * Legacy setter; the flag is now derived by {@link #relinkStack()}.
     *
     * @param value new flag value
     */
    public void setBottom(boolean value)
    {
        isBottom = value;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound)
    {
        compound.setBoolean("bottom", isBottom);
        compound.setBoolean("isTop", isTop);
        return super.writeToNBT(compound);
    }

    /**
     * Reads flags from NBT; on the client a received update schedules a stack relink (the client never sees
     * the server's break/merge logic, so its pointers would otherwise go stale).
     *
     * @param compound saved/synced data
     */
    @Override
    public void readFromNBT(NBTTagCompound compound)
    {
        isBottom = compound.getBoolean("bottom");
        isTop = compound.getBoolean("isTop");
        super.readFromNBT(compound);
        if (world != null && world.isRemote) relinkPending = true;
    }
}
