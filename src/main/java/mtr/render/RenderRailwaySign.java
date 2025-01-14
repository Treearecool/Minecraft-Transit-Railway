package mtr.render;

import mtr.block.BlockRailwaySign;
import mtr.block.BlockStationNameBase;
import mtr.block.IBlock;
import mtr.data.NameColorDataBase;
import mtr.data.Platform;
import mtr.data.Station;
import mtr.entity.EntitySeat;
import mtr.gui.ClientData;
import mtr.gui.IGui;
import net.minecraft.block.BlockState;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;

import java.util.*;
import java.util.stream.Collectors;

public class RenderRailwaySign<T extends BlockRailwaySign.TileEntityRailwaySign> extends BlockEntityRenderer<T> implements IBlock, IGui {

	public RenderRailwaySign(BlockEntityRenderDispatcher dispatcher) {
		super(dispatcher);
	}

	@Override
	public void render(T entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		final WorldAccess world = entity.getWorld();
		if (world == null) {
			return;
		}

		final BlockPos pos = entity.getPos();
		final BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof BlockRailwaySign)) {
			return;
		}
		final BlockRailwaySign block = (BlockRailwaySign) state.getBlock();
		if (entity.getSign().length != block.length) {
			return;
		}
		final Direction facing = IBlock.getStatePropertySafe(state, BlockStationNameBase.FACING);
		final BlockRailwaySign.SignType[] signTypes = entity.getSign();

		matrices.push();
		matrices.translate(0.5, 0.53125, 0.5);
		matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(-facing.asRotation()));
		matrices.multiply(Vector3f.POSITIVE_Z.getDegreesQuaternion(180));
		matrices.translate(block.getXStart() / 16F - 0.5, 0, -0.0625 - SMALL_OFFSET * 3);

		for (int i = 0; i < signTypes.length; i++) {
			if (signTypes[i] != null) {
				IGui.drawRectangle(matrices, vertexConsumers, 0, 0, 0.5F * (signTypes.length), 0.5F, SMALL_OFFSET * 2, facing, ARGB_BLACK, light);

				final int index = i;
				drawSign(matrices, vertexConsumers, dispatcher.getTextRenderer(), pos, signTypes[i], 0.5F * i, 0, 0.5F, i, signTypes.length - i - 1, entity.getSelectedIds(), facing, (x, y, size, flipTexture) -> IGui.drawTexture(matrices, vertexConsumers, signTypes[index].id.toString(), x, y, size, size, flipTexture ? 1 : 0, 0, flipTexture ? 0 : 1, 1, facing, -1, -1));
			}
		}

		matrices.pop();
	}

	@Override
	public boolean rendersOutsideBoundingBox(T blockEntity) {
		return true;
	}

	public static void drawSign(MatrixStack matrices, VertexConsumerProvider vertexConsumers, TextRenderer textRenderer, BlockPos pos, BlockRailwaySign.SignType signType, float x, float y, float size, float maxWidthLeft, float maxWidthRight, Set<Long> selectedIds, Direction facing, DrawTexture drawTexture) {
		if (RenderSeat.shouldNotRender(pos, EntitySeat.DETAIL_RADIUS)) {
			return;
		}

		final float signSize = (signType.small ? BlockRailwaySign.SMALL_SIGN_PERCENTAGE : 1) * size;
		final float margin = (size - signSize) / 2;

		final boolean hasCustomText = signType.hasCustomText;
		final boolean flipped = signType.flipped;
		final boolean flipTexture = flipped && !hasCustomText;

		if (vertexConsumers != null && (signType == BlockRailwaySign.SignType.LINE || signType == BlockRailwaySign.SignType.LINE_FLIPPED)) {
			final Station station = ClientData.getStation(pos);
			if (station == null) {
				return;
			}

			final Map<Integer, ClientData.ColorNamePair> routesInStation = ClientData.routesInStation.get(station.id);
			if (routesInStation != null) {
				final List<ClientData.ColorNamePair> selectedIdsSorted = selectedIds.stream().map(Math::toIntExact).filter(routesInStation::containsKey).map(routesInStation::get).sorted(Comparator.comparingInt(route -> route.color)).collect(Collectors.toList());
				final int selectedCount = selectedIdsSorted.size();

				final float maxWidth = Math.max(0, ((flipped ? maxWidthLeft : maxWidthRight) + 1) * size - margin * 1.5F);
				final List<Float> textWidths = new ArrayList<>();
				for (final ClientData.ColorNamePair route : selectedIdsSorted) {
					IGui.drawStringWithFont(matrices, textRenderer, route.name, HorizontalAlignment.LEFT, VerticalAlignment.CENTER, 0, 10000, -1, size - margin * 3, 1, 0, false, (x1, y1, x2, y2) -> textWidths.add(x2));
				}

				matrices.push();
				matrices.translate(flipped ? x + size - margin : x + margin, 0, 0);

				final float totalTextWidth = textWidths.stream().reduce(Float::sum).orElse(0F) + 1.5F * margin * selectedCount;
				if (totalTextWidth > maxWidth) {
					matrices.scale((maxWidth - margin / 2) / (totalTextWidth - margin / 2), 1, 1);
				}

				float xOffset = margin * 0.5F;
				for (int i = 0; i < selectedIdsSorted.size(); i++) {
					final ClientData.ColorNamePair route = selectedIdsSorted.get(i);
					IGui.drawStringWithFont(matrices, textRenderer, route.name, flipped ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT, VerticalAlignment.TOP, flipped ? -xOffset : xOffset, y + margin * 1.5F, -1, size - margin * 3, 0.01F, ARGB_WHITE, false, (x1, y1, x2, y2) -> IGui.drawRectangle(matrices, vertexConsumers, x1 - margin / 2, y1 - margin / 2, x2 + margin / 2, y2 + margin / 2, SMALL_OFFSET, facing, route.color + ARGB_BLACK, -1));
					xOffset += textWidths.get(i) + margin * 1.5F;
				}

				matrices.pop();
			}
		} else if (vertexConsumers != null && (signType == BlockRailwaySign.SignType.PLATFORM || signType == BlockRailwaySign.SignType.PLATFORM_FLIPPED)) {
			final Station station = ClientData.getStation(pos);
			if (station == null) {
				return;
			}

			final Map<Long, Platform> platformPositions = ClientData.platformsInStation.get(station.id);
			if (platformPositions != null) {
				final List<Platform> selectedIdsSorted = selectedIds.stream().filter(platformPositions::containsKey).map(platformPositions::get).sorted(NameColorDataBase::compareTo).collect(Collectors.toList());
				final int selectedCount = selectedIdsSorted.size();

				final float smallPadding = margin / selectedCount;
				final float height = (size - margin * 2 + smallPadding) / selectedCount;
				for (int i = 0; i < selectedIdsSorted.size(); i++) {
					final float topOffset = i * height + margin;
					final float bottomOffset = (i + 1) * height + margin - smallPadding;
					final RouteRenderer routeRenderer = new RouteRenderer(matrices, vertexConsumers, selectedIdsSorted.get(i), true);
					routeRenderer.renderArrow((flipped ? x - maxWidthLeft * size : x) + margin, (flipped ? x + size : x + (maxWidthRight + 1) * size) - margin, topOffset, bottomOffset, flipped, !flipped, facing, -1, false);
				}
			}
		} else {
			drawTexture.drawTexture(x + margin, y + margin, signSize, flipTexture);

			if (hasCustomText) {
				final float fixedMargin = size * (1 - BlockRailwaySign.SMALL_SIGN_PERCENTAGE) / 2;
				final boolean isSmall = signType.small;
				final float maxWidth = Math.max(0, (flipped ? maxWidthLeft : maxWidthRight) * size - fixedMargin * (isSmall ? 1 : 2));
				final float start = flipped ? x - (isSmall ? 0 : fixedMargin) : x + size + (isSmall ? 0 : fixedMargin);
				IGui.drawStringWithFont(matrices, textRenderer, signType.text, flipped ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT, VerticalAlignment.TOP, start, y + fixedMargin, maxWidth, size - fixedMargin * 2, 0.01F, ARGB_WHITE, false, null);
			}
		}
	}

	@FunctionalInterface
	public interface DrawTexture {
		void drawTexture(float x, float y, float size, boolean flipTexture);
	}
}
