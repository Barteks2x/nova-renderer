package com.continuum.nova.chunks;

import com.continuum.nova.system.NovaNative;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author ddubois
 * @since 27-Jul-17
 */
public interface IGeometryFilter {
    boolean matches(IBlockState blockState);

    boolean matches(NovaNative.mc_gui_buffer guiBuffer);

    class AndGeometryFilter implements IGeometryFilter {
        private IGeometryFilter left;
        private IGeometryFilter right;

        public AndGeometryFilter(IGeometryFilter left, IGeometryFilter right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean matches(IBlockState blockState) {
            return left.matches(blockState) && right.matches(blockState);
        }

        @Override
        public boolean matches(NovaNative.mc_gui_buffer guiBuffer) {
            return left.matches(guiBuffer) && right.matches(guiBuffer);
        }

        @Override
        public String toString() {
            return "(" + left.toString() + " AND " + right.toString() + ")";
        }
    }

    class OrGeometryFilter implements IGeometryFilter {
        private IGeometryFilter left;
        private IGeometryFilter right;

        public OrGeometryFilter(IGeometryFilter left, IGeometryFilter right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean matches(IBlockState blockState) {
            return left.matches(blockState) || right.matches(blockState);
        }

        @Override
        public boolean matches(NovaNative.mc_gui_buffer guiBuffer) {
            return left.matches(guiBuffer) || right.matches(guiBuffer);
        }

        @Override
        public String toString() {
            return "(" + left.toString() + " OR " + right.toString() + ")";
        }
    }

    class NameGeometryFilter implements IGeometryFilter {
        private String name;

        public NameGeometryFilter(String name) {
            this.name = name;
        }

        @Override
        public boolean matches(IBlockState blockState) {
            return blockState.getBlock().getUnlocalizedName().equals(name);
        }

        @Override
        public boolean matches(NovaNative.mc_gui_buffer guiBuffer) {
            // hardcoded but refactoring the system to unify GUI and regular meshes is hard
            return name.equals("gui");
        }

        @Override
        public String toString() {
            return "name::" + name;
        }
    }

    class NamePartGeometryFilter implements IGeometryFilter {
        private String namePart;

        public NamePartGeometryFilter(String namePart) {
            this.namePart = namePart;
        }

        @Override
        public boolean matches(IBlockState blockState) {
            return blockState.getBlock().getUnlocalizedName().contains(namePart);
        }

        @Override
        public boolean matches(NovaNative.mc_gui_buffer guiBuffer) {
            return "gui".contains(namePart);
        }

        @Override
        public String toString() {
            return "name_part::" + namePart;
        }
    }

    class GeometryTypeGeometryFilter implements IGeometryFilter {
        private NovaNative.GeometryType type;

        public GeometryTypeGeometryFilter(NovaNative.GeometryType type) {
            this.type = type;
        }

        @Override
        public boolean matches(IBlockState blockState) {
            return type == NovaNative.GeometryType.BLOCK;
        }

        @Override
        public boolean matches(NovaNative.mc_gui_buffer guiBuffer) {
            switch(type) {
                case GUI:
                    return guiBuffer.texture_name.contains("gui/");
                case TEXT:
                    return guiBuffer.texture_name.contains("font/");
                default:
                    return false;
            }
        }

        @Override
        public String toString() {
            return "geometry_type::" + type.name().toLowerCase();
        }
    }

    /**
     * Matches blocks in the translucent render layer
     */
    class TransparentGeometryFilter implements IGeometryFilter {
        boolean shouldBeTransparent;

        private static final Logger LOG = LogManager.getLogger(TransparentGeometryFilter.class);

        public TransparentGeometryFilter(boolean shouldBeTransparent) {
            this.shouldBeTransparent = shouldBeTransparent;
        }

        @Override
        public boolean matches(IBlockState blockState) {
            return (blockState.getBlock().getBlockLayer() == BlockRenderLayer.TRANSLUCENT) == shouldBeTransparent;
        }

        @Override
        public boolean matches(NovaNative.mc_gui_buffer guiBuffer) {
            // GUI uses all sorts of transparent and semi-transparent textures
            return true;
        }

        @Override
        public String toString() {
            if(shouldBeTransparent) {
                return "transparent";
            } else {
                return "not_transparent";
            }
        }
    }

    class EmissiveGeometryFilter implements IGeometryFilter {
        boolean shouldBeEmissive;

        public EmissiveGeometryFilter(boolean shouldBeEmissive) {
            this.shouldBeEmissive = shouldBeEmissive;
        }

        @Override
        public boolean matches(IBlockState blockState) {
            return blockState.getLightValue() > 0 == shouldBeEmissive;
        }

        @Override
        public boolean matches(NovaNative.mc_gui_buffer guiBuffer) {
            // No the GUI can't emit light shut up
            return false;
        }

        @Override
        public String toString() {
            if(shouldBeEmissive) {
                return "emissive";
            } else {
                return "not_emissive";
            }
        }
    }

    static IGeometryFilter parseFilterString(final String filterString) {
        String[] tokens = filterString.split(" ");

        if(tokens.length % 2 == 0) {
            throw new IllegalArgumentException("Cannot have an even number of tokens in your geometry filter expressions");
        }

        IGeometryFilter filter = makeFilterFromToken(tokens[0]);
        if(tokens.length == 1) {
            return filter;
        }

        return makeFilterExpression(filter, tokens, 1);
    }

    static IGeometryFilter makeFilterExpression(IGeometryFilter previousFilter, String[] tokens, int curToken) {
        IGeometryFilter thisFilter;

        switch(tokens[curToken]) {
            case "AND":
                thisFilter = new AndGeometryFilter(previousFilter, makeFilterFromToken(tokens[curToken + 1]));
                break;

            case "OR":
                thisFilter = new OrGeometryFilter(previousFilter, makeFilterFromToken(tokens[curToken + 1]));
                break;

            default:
                return makeFilterFromToken(tokens[curToken + 1]);
        }

        boolean hasAnotherExpression = curToken + 2 < tokens.length - 1;

        if(hasAnotherExpression) {
            return makeFilterExpression(thisFilter, tokens, curToken + 2);

        } else {
            return thisFilter;
        }
    }

    static IGeometryFilter makeFilterFromToken(final String token) {
        if(token.startsWith("geometry_type::")) {
            String typeName = token.substring(15);
            NovaNative.GeometryType type = NovaNative.GeometryType.valueOf(typeName.toUpperCase());
            return new GeometryTypeGeometryFilter(type);

        } else if(token.startsWith("name::")) {
            String name = token.substring(6);
            return new NameGeometryFilter(name);

        } else if(token.startsWith("name_part::")) {
            String namePart = token.substring(11);
            return new NamePartGeometryFilter(namePart);

        } else if(token.equals("transparent")) {
            return new TransparentGeometryFilter(true);

        } else if(token.equals("not_transparent")) {
            return new TransparentGeometryFilter(false);

        } else if(token.equals("emissive")) {
            return new EmissiveGeometryFilter(true);

        } else if(token.equals("not_emissive")) {
            return new EmissiveGeometryFilter(false);
        }

        throw new IllegalArgumentException("Could not make a filter from token '" + token + "'");
    }
}
