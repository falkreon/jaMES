package blue.endless.james.core.catboy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class GBLoader {
	public static void loadCartridge(InputStream in, GameBoyCore core) throws IOException {
		byte[] cart = in.readAllBytes();
		if (cart.length<=0x147) throw new IOException("Invalid cartridge");
		int mapperType = cart[0x147] & 0xFF;
		
		List<byte[]> banks = DmgMapper.getBanks(cart);
		
		System.out.println("Mapper: 0x"+mapperType+", "+banks.size()+" banks");
		
		
		
		if (mapperType==0x00) {
			DmgMapper mapper = new DmgMapper.None(banks);
			core.mapRom(mapper);
		} else if (mapperType==0x01||mapperType==0x02||mapperType==0x03) {
			DmgMapper mapper = new DmgMapper.Mbc1(banks);
			core.mapRom(mapper);
		} else if (mapperType==0x19 || mapperType==0x1A || mapperType==0x1B) {
			System.out.println("MBC5 cart, "+banks.size()+" banks.");
			DmgMapper mapper = new DmgMapper.Mbc5(banks);
			core.mapRom(mapper);
		} else {
			core.mapRom(cart);
		}
	}
}
