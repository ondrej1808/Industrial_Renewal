package cassiokf.industrialrenewal.blocks;

import cassiokf.industrialrenewal.blocks.abstracts.BlockMultiTankBase;
import cassiokf.industrialrenewal.tileentity.TEStorage;
import cassiokf.industrialrenewal.util.MachinesUtils;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

public class BlockStorage extends BlockMultiTankBase<TEStorage>
{
    public BlockStorage(String name, CreativeTabs tab)
    {
        super(name, tab);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        // patch: the top face works like the sides, except when placing a block on top (stacking)
        if (isPlacingOnTop(facing, playerIn, hand)
                && !rackHolds(worldIn, pos, playerIn.getHeldItem(hand))) return false;
        TileEntity te = worldIn.getTileEntity(pos);
        if (te instanceof TEStorage)
            return ((TEStorage) te).getMaster().getBottomTE().onActivated(playerIn, hand, facing);
        return false;
    }

    /**
     * Whether a click on the top face should be passed through so the held block gets placed
     * (needed to stack another rack on top). Any other click on the top face opens/uses the rack.
     *
     * @param facing clicked face
     * @param player clicking player
     * @param hand   used hand
     * @return {@code true} if the player clicks the top face holding this rack's item (stacking wins)
     */
    private static boolean isPlacingOnTop(EnumFacing facing, EntityPlayer player, EnumHand hand)
    {
        return facing == EnumFacing.UP && player.getHeldItem(hand).getItem() instanceof ItemBlock
                && ((ItemBlock) player.getHeldItem(hand).getItem()).getBlock() instanceof BlockStorage;
    }

    /**
     * Whether the rack at {@code pos} already stores exactly the held item, in which case a top click
     * should insert it instead of placing it.
     *
     * @param world world
     * @param pos   clicked block position
     * @param held  held stack
     * @return {@code true} if the rack contents match the held item
     */
    private static boolean rackHolds(World world, BlockPos pos, ItemStack held)
    {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TEStorage)) return false;
        TEStorage bottom = ((TEStorage) te).getMaster().getBottomTE();
        return bottom.getCount() > 0 && bottom.getStack().getItem() == held.getItem();
    }

    @Override
    public List<BlockPos> getMachineBlockPosList(BlockPos masterPos, EnumFacing facing)
    {
        return MachinesUtils.getBlocksIn3x3x2Centered(masterPos, facing);
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack)
    {
        worldIn.setBlockState(pos.up(), state.withProperty(MASTER, true));
    }

    @Override
    protected BlockStateContainer createBlockState()
    {
        return new BlockStateContainer(this, FACING, MASTER, DOWN);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos)
    {
        boolean isMaster = state.getValue(MASTER);
        return state.withProperty(DOWN, (isMaster ? isBot(worldIn, pos) : 0));
    }

    @Override
    public boolean instanceOf(Block block)
    {
        return block instanceof BlockStorage;
    }

    @Override
    public TEStorage createTileEntity(World world, IBlockState state)
    {
        return new TEStorage();
    }
}
