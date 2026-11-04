package com.hacisimsek.inventory.config;

import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.model.InventoryStatus;
import com.hacisimsek.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds the Mongo 'inventory' collection with stock for the demo catalog.
 * Product UUIDs must match product-service's data.sql so orders placed with
 * catalog product IDs can pass inventory reservation. Idempotent: existing
 * records are never overwritten.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryDataSeeder implements CommandLineRunner {

    private static final int DEFAULT_QUANTITY = 100;
    private static final int DEFAULT_THRESHOLD = 10;
    private static final String DEFAULT_WAREHOUSE = "WH-A1";

    private final InventoryRepository inventoryRepository;

    private static Map<UUID, String> catalog() {
        Map<UUID, String> products = new LinkedHashMap<>();
        products.put(UUID.fromString("850613db-5e9d-4080-bfa6-4311e8c15e7b"), "Samsung Galaxy S26");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000001"), "Samsung Galaxy S26 Ultra");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000002"), "Samsung Galaxy Z Fold 7");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000003"), "Google Pixel 10 Pro");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000004"), "Xiaomi 15 Ultra");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000005"), "Motorola Edge 50 Ultra");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000006"), "Nothing Phone (3)");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000007"), "Samsung Galaxy A56");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000008"), "Apple MacBook Pro 16");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000009"), "Dell XPS 13");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000010"), "Lenovo ThinkPad X1 Carbon");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000011"), "ASUS ROG Zephyrus G16");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000012"), "Apple iPad Pro 13");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000013"), "Samsung Galaxy Tab S10+");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000014"), "Sony WH-1000XM6");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000015"), "Apple AirPods Pro 3");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000016"), "Bose QuietComfort Ultra");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000017"), "JBL Flip 7");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000018"), "Apple Watch Ultra 3");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000019"), "Galaxy Watch 7");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000020"), "Dyson V15 Detect");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000021"), "Ninja Air Fryer Max");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000022"), "Philips 3200 Espresso");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000023"), "Levi's 501 Original");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000024"), "Nike Air Zoom Pegasus 42");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000025"), "Wilson Evolution");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000026"), "Yonex Astrox 99");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000027"), "Atomic Habits");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000028"), "Clean Code");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000029"), "Organic Rolled Oats 1kg");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000030"), "Extra Virgin Olive Oil 500ml");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000031"), "Vitamin C Serum 30ml");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000032"), "SPF 50 Sunscreen 50ml");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000100"), "Sony WH-1000XM5 Wireless Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000101"), "Sony WH-CH720N Wireless ANC Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000102"), "Bose QuietComfort 45 Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000103"), "Sennheiser Momentum 4 Wireless Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000104"), "Sennheiser HD 560S Open-Back Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000105"), "Audio-Technica ATH-M50x Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000106"), "Audio-Technica ATH-M20x Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000107"), "JBL Tune 770NC Wireless Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000108"), "JBL Live 660NC Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000109"), "JBL Tune 510BT Wireless On-Ear Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000110"), "Apple AirPods Max Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000111"), "Beats Studio Pro Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000112"), "Skullcandy Crusher ANC 2 Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000113"), "Skullcandy Hesh ANC Over-Ear Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000114"), "Marshall Major IV Wireless Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000115"), "Bang & Olufsen Beoplay HX Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000116"), "Sony WF-1000XM5 Wireless Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000117"), "Sony LinkBuds S Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000118"), "Apple AirPods 4 Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000119"), "Samsung Galaxy Buds3 Pro Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000120"), "Samsung Galaxy Buds3 Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000121"), "Google Pixel Buds Pro 2 Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000122"), "OnePlus Buds Pro 3 Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000123"), "JBL Wave Buds 2 TWS Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000124"), "boAt Airdopes 800 Pro TWS Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000125"), "Noise Buds N1 Pro TWS Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000126"), "Sony WH-CH520 On-Ear Wireless Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000127"), "Sony WH-1000XM4 Wireless Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000128"), "Audio-Technica ATH-R70x Reference Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000129"), "Beyerdynamic DT 770 Pro Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000130"), "Beyerdynamic DT 990 Pro Open-Back Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000131"), "Philips PH805 Wireless ANC Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000132"), "Anker Soundcore Life Q30 Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000133"), "Anker Soundcore Liberty 4 NC Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000134"), "Nothing Ear (3) Wireless Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000135"), "Jabra Elite 8 Active Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000136"), "Sennheiser Momentum True Wireless 4 Earbuds");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000137"), "Bose QuietComfort Earbuds II");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000138"), "Denon AH-D5200 Reference Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000139"), "Shure SRH840 Professional Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000140"), "Hifiman Sundara Planar Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000141"), "Shokz OpenRun Pro Bone Conduction Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000142"), "Jabra Evolve2 75 UC Bluetooth Headset");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000143"), "Poly Voyager 5200 Bluetooth Headset");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000144"), "Razer Barracuda X Wireless Gaming Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000145"), "HyperX Cloud III Gaming Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000146"), "Logitech G Astro A50 X Gaming Headset");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000147"), "SteelSeries Arctis Nova Pro Wireless Headset");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000148"), "EPOS H6Pro Open-Acoustic Gaming Headset");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000149"), "Corsair Void RGB Elite Wireless Gaming Headphones");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000150"), "Sony PlayStation 5 Slim Console");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000151"), "Xbox Series X Console");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000152"), "Nintendo Switch OLED Console");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000153"), "Steam Deck OLED Handheld");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000154"), "ASUS ROG Ally X Handheld");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000155"), "Lenovo Legion Go Handheld");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000156"), "Xbox Elite Wireless Controller Series 2");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000157"), "Sony DualSense Edge Controller");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000158"), "Nintendo Switch Pro Controller");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000159"), "8BitDo Ultimate C Controller");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000160"), "Logitech G502 X Plus Gaming Mouse");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000161"), "Logitech G Pro X Superlight Gaming Mouse");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000162"), "Razer DeathAdder V3 Pro Gaming Mouse");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000163"), "SteelSeries Aerox 5 Gaming Mouse");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000164"), "Corsair M65 RGB Ultra Gaming Mouse");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000165"), "Razer BlackWidow V4 Pro Mechanical Keyboard");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000166"), "Corsair K70 RGB PRO Mechanical Keyboard");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000167"), "Logitech G915 TKL Wireless Mechanical Keyboard");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000168"), "SteelSeries Apex Pro TKL Keyboard");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000169"), "Keychron Q3 Mechanical Keyboard");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000170"), "ASUS TUF Gaming M3 Mouse");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000171"), "Xbox Wireless Controller Carbon Black");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000172"), "HyperX Alloy Origins 65 Keyboard");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000173"), "Razer Wolverine V2 Chroma Controller");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000174"), "Hori GameCube Style Controller");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000175"), "Logitech G29 Racing Wheel and Pedals");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000176"), "Thrustmaster T248 Racing Wheel");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000177"), "Samsung Odyssey G9 OLED Gaming Monitor");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000178"), "LG UltraGear 27 Gaming Monitor");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000179"), "ASUS ROG Swift PG27AQDM Gaming Monitor");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000180"), "Alienware AW3423DW QD-OLED Gaming Monitor");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000181"), "MSI G274QRF-QD Gaming Monitor");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000182"), "Acer Predator X27U Gaming Monitor");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000183"), "Gigabyte M27Q Gaming Monitor");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000184"), "Secretlab Titan Evo Gaming Chair");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000185"), "DXRacer Master Gaming Chair");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000186"), "Thermaltake XFit Pro Gaming Chair");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000187"), "Respawn 110 Gaming Chair");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000188"), "GTRacing GT890 HD Gaming Chair");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000189"), "Roccat Kone XP Air Gaming Mouse");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000190"), "Glorious Model O2 Wireless Gaming Mouse");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000191"), "HyperX Cloud Alpha Pro Gaming Headset");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000192"), "ASUS ROG Harpe Ace Gaming Mouse");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000193"), "NZXT Kraken Elite 360 AIO Cooler");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000194"), "Cooler Master MasterLiquid ML360 AIO Cooler");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000195"), "Corsair iCUE H150i Elite AIO Cooler");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000196"), "DeepCool LS520 WH ARGB AIO Cooler");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000197"), "Lian Li Galahad II Trinity AIO Cooler");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000198"), "Fractal Design Lumen S36 AIO Cooler");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000199"), "HyperX Pulsefire Haste 2 Gaming Mouse");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000200"), "Sonos Era 100 Smart Speaker");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000201"), "Sonos Beam Gen 2 Soundbar");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000202"), "JBL Charge 5 Portable Speaker");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000203"), "JBL PartyBox 310 Party Speaker");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000204"), "Bose SoundLink Flex Speaker");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000205"), "Bose Smart Soundbar 600");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000206"), "Sony SRS-XB100 Wireless Speaker");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000207"), "Sony HT-A5000 Soundbar");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000208"), "Marshall Stanmore III Wireless Speaker");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000209"), "Ultimate Ears Boom 4 Speaker");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000210"), "Anker Soundcore Motion Boom Speaker");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000211"), "Samsung HW-Q800C Soundbar");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000212"), "LG SC9S Soundbar");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000213"), "boAt Stone 1200 Speaker");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000214"), "JBL Clip 5 Speaker");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000215"), "Zebronics Zeb-Juke Bar 8500 Soundbar");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000220"), "The North Face McMurdo Winter Jacket");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000221"), "Columbia Penns Creek Winter Jacket");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000222"), "Levi's Men Winter Puffer Jacket");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000223"), "Wildcraft Halt Winter Jacket");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000224"), "Decathlon Quechua Down Winter Jacket");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000225"), "Woolrich Arctic Parka Winter Jacket");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000226"), "Moncler Maya Down Winter Jacket");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000230"), "Sula Cabernet Shiraz Red Wine 750ml");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000231"), "Sula Vineyards Dindori Reserve Shiraz Red Wine");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000232"), "Grover Zampa Soiree Noir Red Wine 750ml");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000233"), "Grover Zampa La Reserve Cabernet Sauvignon Red Wine");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000234"), "Fratelli Sangiovese Red Wine 750ml");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000235"), "Jacob's Creek Shiraz Cabernet Red Wine 750ml");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000236"), "Ruffino Chianti Red Wine 750ml");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000240"), "Optimum Nutrition Gold Standard Whey Protein 2lb");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000241"), "MyProtein Impact Whey Protein 1kg");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000242"), "Dymatize ISO 100 Hydrolyzed Protein Powder");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000243"), "MuscleBlaze Biozyme Whey Protein");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000244"), "MuscleTech NitroTech Protein Powder");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000245"), "Vega Plant Based Protein Shake");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000246"), "Labrada Lean Body Protein Shake");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000250"), "Ultra-Soft Fleece Blanket Queen Size");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000251"), "Plush Sherpa Fleece Throw Blanket");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000252"), "Electric Heated Blanket with Timer");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000253"), "Wool Blend Winter Blanket");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000254"), "Microfiber Quilted Warm Blanket");
        products.put(UUID.fromString("20000000-0000-4000-8000-000000000255"), "Flannel Fleece Heated Smart Blanket");
        return products;
    }

    @Override
    public void run(String... args) {
        Map<UUID, String> products = catalog();
        long missing = products.keySet().stream()
                .filter(productId -> inventoryRepository.findByProductId(productId).isEmpty())
                .count();

        if (missing == 0) {
            log.info("Inventory seed: no products missing — nothing to do");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        products.forEach((productId, name) -> {
            if (inventoryRepository.findByProductId(productId).isPresent()) {
                return;
            }
            InventoryItem item = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .productId(productId)
                    .name(name)
                    .description(name)
                    .availableQuantity(DEFAULT_QUANTITY)
                    .reservedQuantity(0)
                    .warehouseLocation(DEFAULT_WAREHOUSE)
                    .lowStockThreshold(DEFAULT_THRESHOLD)
                    .status(InventoryStatus.IN_STOCK)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            inventoryRepository.save(item);
        });

        log.info("Inventory seed: inserted {} catalog products with {} units each",
                missing, DEFAULT_QUANTITY);
    }
}