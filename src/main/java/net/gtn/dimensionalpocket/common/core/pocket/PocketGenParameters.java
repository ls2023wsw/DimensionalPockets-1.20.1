package net.gtn.dimensionalpocket.common.core.pocket;

import net.gtn.dimensionalpocket.common.core.utils.CoordSet;
import net.minecraft.core.Direction;

/**
 * 口袋 chunk 分配的进度游标，随存档持久化到 {@code <worldDir>/dimpockets/pocketGenParameters.json}。
 *
 * <p>1.7.10 origin: {@code PocketRegistry.PocketGenParameters}（包级私有静态嵌套类）。
 * 契约 §3.5 把它提成独立公开类，字段也改成 public，Gson 直接反射字段：
 * <pre>{"currentChunk":{"x":0,"y":0,"z":0},"nextPocketCoordsDirection":"NORTH"}</pre>
 *
 * <p>初值必须与上游一致（报告 01 §1.2）：{@code currentChunk = (0,0,0)}、方向 = {@code NORTH}。
 * 新世界的第一个口袋因此落在 y=0（Y 0..15），第二个起才走 {@link PocketRegistry} 的螺旋推进（y=4）。
 */
public class PocketGenParameters {

    /** 上一个已分配的口袋 chunk 坐标。 */
    public CoordSet currentChunk = new CoordSet(0, 0, 0);

    /** 螺旋推进的方向游标。 */
    public Direction nextPocketCoordsDirection = Direction.NORTH;

    @Override
    public String toString() {
        return "PocketGenParameters[currentChunk=" + currentChunk + ", nextPocketCoordsDirection=" + nextPocketCoordsDirection + "]";
    }
}
