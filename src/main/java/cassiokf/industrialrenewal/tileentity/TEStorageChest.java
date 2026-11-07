package cassiokf.industrialrenewal.tileentity;

import cassiokf.industrialrenewal.IndustrialRenewal;
import cassiokf.industrialrenewal.config.IRConfig;
import cassiokf.industrialrenewal.init.GUIHandler;
import cassiokf.industrialrenewal.tileentity.abstracts.TEMultiTankBase;
import cassiokf.industrialrenewal.util.InventoryHandler;
import cassiokf.industrialrenewal.util.MachinesUtils;
import cassiokf.industrialrenewal.util.Utils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;

import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

public class TEStorageChest extends TEMultiTankBase<TEStorageChest>
{
    private static final int slots = 99;
    public final InventoryHandler inventory = new InventoryHandler(slots)
    {
        @Override
        protected void onContentsChanged(int slot)
        {
            TEStorageChest.this.markDirty();
        }

        @Override
        public void itemsToSpawn(List<ItemStack> list)
        {
            for (ItemStack item : list) TEStorageChest.this.spawnItems(item);
        }
    };
    public int additionalLines;
    public int currentLine;
    public String search = "";
    public boolean searchActive = IRConfig.MainConfig.Main.searchBarStartFocused;

    @Override
    public List<BlockPos> getListOfBlockPositions(BlockPos centerPosition)
    {
        return MachinesUtils.getBlocksIn3x3x2Centered(centerPosition, getMasterFacing());
    }

    /** button ids >= this jump directly to line (id - LINE_JUMP_BASE); used by search */
    public static final int LINE_JUMP_BASE = 100;

    /**
     * Handles GUI navigation: 1 = line up, 2 = line down, >= {@link #LINE_JUMP_BASE} = jump to a line.
     *
     * @param id     button id
     * @param player player to reopen the GUI for (null on client)
     */
    public void guiButtonClick(int id, EntityPlayer player)
    {
        if (id >= LINE_JUMP_BASE)
        {
            currentLine = Math.max(0, Math.min(additionalLines, id - LINE_JUMP_BASE));
        }
        else if (id == 1 && currentLine > 0)
        {
            currentLine--;
        }
        else if (id == 2 && currentLine < additionalLines)
        {
            currentLine++;
        }
        openGui(player, false);
    }

    public void openGui(EntityPlayer player, boolean resetLine)
    {
        if (this.isInvalid()) return;
        if (resetLine)
        {
            currentLine = 0;
            search = "";
            searchActive = IRConfig.MainConfig.Main.searchBarStartFocused;
        }
        if (!world.isRemote)
        {
            player.openGui(IndustrialRenewal.instance, GUIHandler.STORAGECHEST, world, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    public void setLineValues(int currentLine, int additionalLines)
    {
        this.currentLine = currentLine;
        this.additionalLines = additionalLines;
    }

    @Override
    public void setSize(int i)
    {
        int newCapacity = slots * i;
        if (newCapacity < 0) newCapacity = Integer.MAX_VALUE;

        additionalLines = (newCapacity / 11) - 6;
        currentLine = 0;
        inventory.setInvSize(newCapacity);
        //NetworkHandler.INSTANCE.sendToAll(new PacketStorageChest(this));
        markDirty();
    }

    @Override
    public void onMasterBreak()
    {
        // contents are moved to the remaining stack parts / dropped by TEMultiTankBase; this only drops
        // anything that is still left (normally nothing)
        super.onMasterBreak();
        Utils.dropInventoryItems(world, pos, inventory);
    }

    /**
     * Empties this chest's own inventory.
     *
     * @return removed stacks
     */
    @Override
    public List<ItemStack> extractAllContents()
    {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < inventory.getSlots(); i++)
        {
            ItemStack s = inventory.getStackInSlot(i);
            if (!s.isEmpty())
            {
                out.add(s.copy());
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        return out;
    }

    /**
     * Inserts stacks into this chest's own inventory, stacking onto existing stacks first.
     *
     * @param stacks stacks to insert
     * @return stacks (or remainders) that did not fit
     */
    @Override
    public List<ItemStack> insertContents(List<ItemStack> stacks)
    {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack s : stacks)
        {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(inventory, s.copy(), false);
            if (!rest.isEmpty()) left.add(rest);
        }
        return left;
    }

    private void spawnItems(ItemStack stack)
    {
        if (!stack.isEmpty()) Utils.spawnItemStack(world, pos.offset(getMasterFacing().getOpposite(), 2), stack);
    }

    @Override
    public boolean instanceOf(TileEntity tileEntity)
    {
        return tileEntity instanceof TEStorageChest;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing)
    {
        if (capability.equals(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY))
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(getMaster().getBottomTE().inventory);
        return super.getCapability(capability, facing);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound)
    {
        compound.setTag("inv", inventory.serializeNBT());
        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound)
    {
        inventory.deserializeNBT(compound.getCompoundTag("inv"));
        super.readFromNBT(compound);
    }
}
