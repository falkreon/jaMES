package blue.endless.james.core.felines.mapper;

/**
 * Corresponds to mirror types listed in https://wiki.nesdev.com/w/index.php/Mirroring#Nametable_Mirroring
 */
public interface Mirror {
	
	public long map(long logicalAddr);
	
	
	public static Mirror HORIZONTAL = (phys) -> {
		if (phys<0x2000) {
			return 0;
		}
		
		long mapped = phys - 0x2000;
		
		if (mapped < 0x3FF) {        //000-3FF
			//nametable 0
			return mapped;
		} else if (mapped < 0x7FF) { //400-7FF
			//nametable 0
			return (mapped - 0x400);
		} else if (mapped < 0xBFF) { //800-BFF
			//nametable 1
			return (mapped-0x800) + 0x400;
		} else {                     //C00-FFF
			//nametable 1
			return (mapped-0xC00) + 0x400;
		}
	};
	
	public static Mirror VERTICAL = (phys) -> {
		if (phys<0x2000) {
			return 0;
		}
		
		long mapped = phys - 0x2000;
		
		if (mapped < 0x3FF) {        //000-3FF
			//nametable 0
			return mapped;
		} else if (mapped < 0x7FF) { //400-7FF
			//nametable 1
			return mapped;
		} else if (mapped < 0xBFF) { //800-BFF
			//nametable 0
			return (mapped-0x800) + 0x400;
		} else {                     //C00-FFF
			//nametable 1
			return (mapped-0x800) + 0x400;
		}
	};
	/*
	HORIZONTAL,
	VERTICAL,
	SINGLE_SCREEN,
	FOUR_SCREEN,
	DIAGONAL,
	L_SHAPED,
	THREE_SCREEN_VERTICAL,
	THREE_SCREEN_HORIZONTAL,
	THREE_SCREEN_DIAGONAL,
	SINGLE_SCREEN_FIXED
	;*/
}
