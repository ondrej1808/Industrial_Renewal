package cassiokf.industrialrenewal.util;

import cassiokf.industrialrenewal.util.interfaces.ISync;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;

public class MultiStackHandler extends ItemStackHandler
{
    private final TileEntity te;
    private int maxStack;
    private boolean sync;
    private int count;

    public MultiStackHandler(int maxStack, boolean sync, TileEntity te)
    {
        super(1);
        this.maxStack = maxStack;
        this.sync = sync;
        this.te = te;
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int slot)
    {
        validateSlotIndex(slot);
        // patch: the internal stack is only the item "type" (count 1); return a copy with the real count.
        // The old code mutated and returned the internal stack (and even ItemStack.EMPTY), so any caller
        // modifying it, or a save while its size was 0, lost the item type -> whole rack content vanished.
        ItemStack type = this.stacks.get(slot);
        if (count <= 0 || type.isEmpty()) return ItemStack.EMPTY;
        return ItemHandlerHelper.copyStackWithSize(type, count);
    }

    /**
     * Clears the stored item type when the count dropped to zero (or below).
     *
     * @return nothing
     */
    private void clearIfEmpty()
    {
        if (count <= 0)
        {
            count = 0;
            this.stacks.set(0, ItemStack.EMPTY);
        }
    }

    public ItemStack insertItem(@Nonnull ItemStack stack)
    {
        return insertItem(0, stack, false);
    }

    public ItemStack insertItem(@Nonnull ItemStack stack, boolean simulate)
    {
        return insertItem(0, stack, simulate);
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate)
    {
        if (stack.isEmpty())
            return ItemStack.EMPTY;

        validateSlotIndex(0);

        ItemStack existing = this.stacks.get(0);

        int limit = getStackLimit(0, stack);

        if (!existing.isEmpty())
        {
            if (!ItemHandlerHelper.canItemStacksStack(stack, existing))
                return stack;

            limit -= count;
        }

        if (limit <= 0)
            return stack;

        boolean reachedLimit = stack.getCount() > limit;

        if (!simulate)
        {
            if (existing.isEmpty())
            {
                this.stacks.set(0, ItemHandlerHelper.copyStackWithSize(stack, 1));
                count = Math.min(limit, stack.getCount());
            }
            else
            {
                count += reachedLimit ? limit : stack.getCount();
            }
            onContentsChanged(0);
        }

        return reachedLimit ? ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - limit) : ItemStack.EMPTY;
    }

    public ItemStack extractItem(int amount)
    {
        return extractItem(0, amount, false);
    }

    public ItemStack extractItem(int amount, boolean simulate)
    {
        return extractItem(0, amount, simulate);
    }

    @Override
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate)
    {
        if (amount == 0)
            return ItemStack.EMPTY;

        validateSlotIndex(0);

        ItemStack existing = this.stacks.get(0);

        if (existing.isEmpty())
            return ItemStack.EMPTY;

        int toExtract = Math.min(amount, existing.getMaxStackSize());

        if (count <= toExtract)
        {
            int toRemove = count;
            if (!simulate)
            {
                this.stacks.set(0, ItemStack.EMPTY);
                count = 0;
                onContentsChanged(0);
            }
            return ItemHandlerHelper.copyStackWithSize(existing, toRemove);
        }
        else
        {
            if (!simulate)
            {
                count -= toExtract;
                onContentsChanged(0);
            }
            return ItemHandlerHelper.copyStackWithSize(existing, toExtract);
        }
    }

    @Override
    public int getSlotLimit(int slot)
    {
        return maxStack;
    }

    public int getCount()
    {
        return count;
    }

    public void setCount(int value)
    {
        count = value;
        clearIfEmpty();
        onContentsChanged(0);
    }

    public void removeFromCount(int value)
    {
        count -= value;
        clearIfEmpty();
        onContentsChanged(0);
    }

    /**
     * Removes everything from the handler.
     *
     * @return the removed contents as one stack with the full count (may exceed max stack size), or EMPTY
     */
    public ItemStack extractAll()
    {
        ItemStack all = getStackInSlot(0);
        count = 0;
        this.stacks.set(0, ItemStack.EMPTY);
        onContentsChanged(0);
        return all;
    }

    public void addToCount(int value)
    {
        count += value;
        onContentsChanged(0);
    }

    public void setSlotLimit(int value)
    {
        maxStack = value;
        onContentsChanged(0);
    }

    @Override
    protected int getStackLimit(int slot, @Nonnull ItemStack stack)
    {
        return getSlotLimit(0);
    }

    @Override
    protected void onContentsChanged(int slot)
    {
        super.onContentsChanged(slot);
        if (sync && te instanceof ISync) ((ISync) te).sync();
        else te.markDirty();
    }

    @Override
    public NBTTagCompound serializeNBT()
    {
        NBTTagCompound compound = super.serializeNBT();
        compound.setInteger("maxStack", maxStack);
        compound.setBoolean("sync", sync);
        compound.setInteger("count", count);
        return compound;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt)
    {
        maxStack = nbt.getInteger("maxStack");
        sync = nbt.getBoolean("sync");
        count = nbt.getInteger("count");
        super.deserializeNBT(nbt);
        // patch: old saves stored the internal stack with the real count truncated to a byte (e.g. 256 -> 0),
        // which makes it "empty" although the item id is there. Keep the type (count 1) if items are stored.
        ItemStack type = this.stacks.get(0);
        if (type != ItemStack.EMPTY && type.getItem() != Items.AIR && count > 0)
            type.setCount(1);
        else if (count <= 0) this.stacks.set(0, ItemStack.EMPTY);
    }
}
