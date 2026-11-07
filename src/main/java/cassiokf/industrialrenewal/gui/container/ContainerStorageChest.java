package cassiokf.industrialrenewal.gui.container;

import cassiokf.industrialrenewal.tileentity.TEStorageChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerStorageChest extends ContainerBase
{
    private final TEStorageChest te;
    private final IItemHandler builtFor;
    private final int builtSlots;

    public ContainerStorageChest(IInventory playerInv, TEStorageChest te)
    {
        IItemHandler inventory = te.inventory; //Gets the inventory from our tile entity
        this.te = te;
        this.builtFor = inventory;
        this.builtSlots = inventory.getSlots();
        int startIndex = te.currentLine * 11;
        int numRows = inventory.getSlots() / 11;
        int limit = 0;
        int xS = 0;
        int yS = 0;
        for (int y = 0; y < numRows; ++y)
        {
            for (int x = 0; x < 11; ++x)
            {
                int index = x + y * 11;
                if (index >= startIndex && limit < 66)
                {
                    limit++;
                    xS = x;
                    this.addSlotToContainer(new SlotItemHandler(inventory, index, 8 + xS * 18, 16 + yS * 18));
                }
                else
                    this.addSlotToContainer(new SlotItemHandler(inventory, index, Integer.MIN_VALUE, Integer.MIN_VALUE));
            }
            if (xS > 0) yS++;
        }
        drawPlayerInv(playerInv, 45, 18);
    }

    /**
     * Patch: the GUI closes when the rack is broken or the stack is resized (rack added/removed),
     * because the slots were built for the old inventory size (stale slots crashed / mis-addressed items).
     *
     * @param player player using the container
     * @return {@code false} when the container must be closed
     */
    @Override
    public boolean canInteractWith(EntityPlayer player)
    {
        return super.canInteractWith(player) && !te.isInvalid() && te.inventory == builtFor
                && te.inventory.getSlots() == builtSlots;
    }
}
