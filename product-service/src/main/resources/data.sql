-- ============================================================
-- Seed catalog: categories + products for the Zexxity demo.
-- Idempotent: safe to run on every startup (ON CONFLICT DO NOTHING).
-- Fixed UUIDs so API-consumers and inventory-service can reference
-- products/categories deterministically.
-- ============================================================

-- Remove test-junk duplicate categories (typo variants of "Smartphones")
DELETE FROM categories WHERE slug IN ('smart', 'smartp', 'smartph', 'smartpho', 'smartphon');

-- ── Categories ─────────────────────────────────────────────────
INSERT INTO categories (id, name, description, slug, parent_id, created_at, updated_at) VALUES
    ('244fc480-6642-443d-a91a-6a7ed5cf00bf', 'Electronics',    'Gadgets and consumer electronics', 'electronics',       NULL, NOW(), NOW()),
    ('4f9334f0-3fb3-45e7-acc4-f1d3183986cd', 'Smartphones',    'Latest smartphones from every brand', 'smartphones',     NULL, NOW(), NOW()),
    ('ba9bb9eb-2139-4eff-8917-b12a13253e39', 'Android Phones', 'Android-powered mobile phones', 'android-phones',      NULL, NOW(), NOW()),
    ('30000000-0000-4000-8000-000000000001', 'Laptops',        'Ultrabooks and gaming laptops', 'laptops',           NULL, NOW(), NOW()),
    ('30000000-0000-4000-8000-000000000002', 'Tablets',        'Tablets and convertibles', 'tablets',              NULL, NOW(), NOW()),
    ('30000000-0000-4000-8000-000000000003', 'Audio',          'Headphones, earbuds and speakers', 'audio',         NULL, NOW(), NOW()),
    ('30000000-0000-4000-8000-000000000004', 'Wearables',      'Smartwatches and fitness trackers', 'wearables',     NULL, NOW(), NOW()),
    ('16902028-4ba5-43d0-85d0-9a1627badff6', 'Home & Kitchen', 'Appliances and cookware for the home', 'home-kitchen', NULL, NOW(), NOW()),
    ('c8bb2a78-4476-4e03-b241-e302270dbc82', 'Clothing',       'Apparel and fashion', 'clothing',                NULL, NOW(), NOW()),
    ('2784929e-d8bd-4c53-8eea-452d99406aa1', 'Sports',         'Sports equipment and gear', 'sports',           NULL, NOW(), NOW()),
    ('b88dffe1-3ec3-47bb-955e-8f8269701ccf', 'Books',          'Books and reading', 'books',                     NULL, NOW(), NOW()),
    ('30000000-0000-4000-8000-000000000005', 'Groceries',      'Pantry staples and groceries', 'groceries',       NULL, NOW(), NOW()),
    ('30000000-0000-4000-8000-000000000006', 'Beauty',         'Skincare and beauty essentials', 'beauty',        NULL, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- ── Products ───────────────────────────────────────────────────
INSERT INTO products (id, name, description, sku, price, original_price, category_id, seller_id, status, image_url, brand, weight_grams, average_rating, rating_count, created_at, updated_at) VALUES
    -- Smartphones
    ('850613db-5e9d-4080-bfa6-4311e8c15e7b', 'Samsung Galaxy S26',        '6.8-inch AMOLED, 200MP camera, 5000mAh battery.', 'SGS26-256',        84999.00, 92999.00, '4f9334f0-3fb3-45e7-acc4-f1d3183986cd', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/galaxy-s26/640/480',            'Samsung',  218, 4.60, 128, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000001', 'Samsung Galaxy S26 Ultra',  '200MP camera, S-Pen, titanium build, 1TB storage.', 'SGS26U-256-TTB',   129999.00, 139999.00, '4f9334f0-3fb3-45e7-acc4-f1d3183986cd', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/galaxy-s26-ultra/640/480',       'Samsung',  218, 4.80,  89, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000002', 'Samsung Galaxy Z Fold 7',   'Foldable 7.6-inch display, dual batteries.', 'SZFOLD7-512',        164999.00, 174999.00, '4f9334f0-3fb3-45e7-acc4-f1d3183986cd', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/galaxy-zfold7/640/480',           'Samsung',  239, 4.70,  54, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000003', 'Google Pixel 10 Pro',       'Tensor chip, 50MP camera, 7 years of updates.', 'GPXL10P-256',       99999.00, 104999.00, '4f9334f0-3fb3-45e7-acc4-f1d3183986cd', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/pixel-10-pro/640/480',            'Google',   199, 4.50,  76, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000004', 'Xiaomi 15 Ultra',            '1-inch Leica camera, Snapdragon 8 Elite.', 'XM15U-256',          89999.00,  94999.00, '4f9334f0-3fb3-45e7-acc4-f1d3183986cd', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/xiaomi-15-ultra/640/480',          'Xiaomi',   226, 4.40,  62, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000005', 'Motorola Edge 50 Ultra',     'Folding display tech, 144Hz OLED.', 'MTRLE50U-512',          59999.00,  64999.00, '4f9334f0-3fb3-45e7-acc4-f1d3183986cd', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/moto-edge50u/640/480',             'Motorola', 197, 4.30,  41, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000006', 'Nothing Phone (3)',          'Glyph interface, transparent design.', 'NTPHN3-256',         45999.00,  49999.00, '4f9334f0-3fb3-45e7-acc4-f1d3183986cd', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/nothing-phone3/640/480',           'Nothing',  222, 4.20,  35, NOW(), NOW()),
    -- Android Phones
    ('20000000-0000-4000-8000-000000000007', 'Samsung Galaxy A56',         'Mid-range all-rounder with AMOLED.', 'SGA56-128',           34999.00,  37999.00, 'ba9bb9eb-2139-4eff-8917-b12a13253e39', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/galaxy-a56/640/480',               'Samsung',  197, 4.10,  18, NOW(), NOW()),
    -- Laptops
    ('20000000-0000-4000-8000-000000000008', 'Apple MacBook Pro 16',       'M4 Max, 48GB RAM, 1TB SSD, Liquid Retina XDR.', 'MBPM4-16-512',     249999.00, 269999.00, '30000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/macbook-pro16/640/480',            'Apple',    2160, 4.90, 210, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000009', 'Dell XPS 13',                'InfinityEdge OLED, Intel Core Ultra 7.', 'DLXPS13-2026',        149999.00, 159999.00, '30000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/dell-xps13/640/480',               'Dell',     1190, 4.50,  95, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000010', 'Lenovo ThinkPad X1 Carbon',  'Business ultrabook, 2.8K display, 17h battery.', 'LNTPX1C-G12',      189999.00, 199999.00, '30000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/thinkpad-x1c/640/480',             'Lenovo',   1100, 4.60,  67, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000011', 'ASUS ROG Zephyrus G16',      'RTX 5080, 240Hz OLED for creators and gamers.', 'ASUSRZ16-2026',     179999.00, 189999.00, '30000000-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/rog-zephyrus-g16/640/480',         'ASUS',     1650, 4.70,  51, NOW(), NOW()),
    -- Tablets
    ('20000000-0000-4000-8000-000000000012', 'Apple iPad Pro 13',          'M4 chip, Tandem OLED, 5G ready.', 'IPDPR13-M4',          109900.00, 119900.00, '30000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/ipad-pro13/640/480',               'Apple',    582, 4.80, 132, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000013', 'Samsung Galaxy Tab S10+',    'AMOLED display, S-Pen included.', 'SGTABS10P',            84999.00,  89999.00, '30000000-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/tab-s10plus/640/480',              'Samsung',  626, 4.50,  44, NOW(), NOW()),
    -- Audio
    ('20000000-0000-4000-8000-000000000014', 'Sony WH-1000XM6',            'Industry-leading noise cancellation.', 'SNYWH1000X6',         34999.00,  37999.00, '30000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/sony-wh1000xm6/640/480',           'Sony',     250, 4.90, 340, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000015', 'Apple AirPods Pro 3',        'Adaptive audio, USB-C, 2x noise cancelling.', 'APPLAP3',           21999.00,  22999.00, '30000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/airpods-pro3/640/480',             'Apple',     70, 4.80, 520, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000016', 'Bose QuietComfort Ultra',    'Premium ANC, spatial audio.', 'BOSQCU-HSE',              37999.00,  40999.00, '30000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/bose-qc-ultra/640/480',            'Bose',     250, 4.70, 188, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000017', 'JBL Flip 7',                 'Portable waterproof Bluetooth speaker.', 'JBLFLP7',            11999.00,  12999.00, '30000000-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/jbl-flip7/640/480',                'JBL',      580, 4.50, 96, NOW(), NOW()),
    -- Wearables
    ('20000000-0000-4000-8000-000000000018', 'Apple Watch Ultra 3',        'Titanium, 49mm, dual-frequency GPS.', 'AWWU3-49',            89999.00,  94999.00, '30000000-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/aw-ultra3/640/480',                'Apple',     61, 4.80,  77, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000019', 'Galaxy Watch 7',             'Sleep tracking, BioActive sensor, 40mm.', 'SGW7-BLK',          39999.00,  42999.00, '30000000-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/gw7/640/480',                      'Samsung',   45, 4.40,  65, NOW(), NOW()),
    -- Home & Kitchen
    ('20000000-0000-4000-8000-000000000020', 'Dyson V15 Detect',           'Cordless vacuum with laser dust detection.', 'DYSNV15',           54999.00,  59999.00, '16902028-4ba5-43d0-85d0-9a1627badff6', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/dyson-v15/640/480',                'Dyson',    7000, 4.60, 145, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000021', 'Ninja Air Fryer Max',         '9L dual-zone air fryer, XL.', 'NINAF9L',                18999.00,  21999.00, '16902028-4ba5-43d0-85d0-9a1627badff6', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/ninja-airfryer/640/480',            'Ninja',    5600, 4.50, 112, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000022', 'Philips 3200 Espresso',      'Bean-to-cup LatteGo with 5 drinks.', 'PHLP3200',            79999.00,  89999.00, '16902028-4ba5-43d0-85d0-9a1627badff6', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/philips-3200/640/480',             'Philips',  9000, 4.70,  38, NOW(), NOW()),
    -- Clothing
    ('20000000-0000-4000-8000-000000000023', 'Levi''s 501 Original',       'Classic 501 straight-fit jeans.', 'LV501-30X32',           4599.00,   4999.00, 'c8bb2a78-4476-4e03-b241-e302270dbc82', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/levis-501/640/480',                'Levi''s',   500, 4.60, 210, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000024', 'Nike Air Zoom Pegasus 42',   'Road running shoe for everyday miles.', 'NKPEG42-42E',         12499.00,  13999.00, 'c8bb2a78-4476-4e03-b241-e302270dbc82', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/nike-peg42/640/480',               'Nike',      320, 4.50, 87, NOW(), NOW()),
    -- Sports
    ('20000000-0000-4000-8000-000000000025', 'Wilson Evolution',           'Official-size composite basketball.', 'WLNEVO-BSK',           6999.00,   7499.00, '2784929e-d8bd-4c53-8eea-452d99406aa1', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/wilson-evolution/640/480',         'Wilson',    620, 4.70,  62, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000026', 'Yonex Astrox 99',            'Pro badminton racket with ultra grip.', 'YNXAX99',            19999.00,  21999.00, '2784929e-d8bd-4c53-8eea-452d99406aa1', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/yonex-astrox99/640/480',           'Yonex',      85, 4.80,  29, NOW(), NOW()),
    -- Books
    ('20000000-0000-4000-8000-000000000027', 'Atomic Habits',              'An easy & proven way to build good habits.', 'BKSATH-HARD',          799.00,    999.00, 'b88dffe1-3ec3-47bb-955e-8f8269701ccf', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/atomic-habits/640/480',            'Penguin',   450, 4.90, 320, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000028', 'Clean Code',                 'A handbook of agile software craftsmanship.', 'BKSCC-3RD',         1299.00,   1599.00, 'b88dffe1-3ec3-47bb-955e-8f8269701ccf', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/clean-code/640/480',               'Pearson',   780, 4.80, 240, NOW(), NOW()),
    -- Groceries
    ('20000000-0000-4000-8000-000000000029', 'Organic Rolled Oats 1kg',    'Whole-grain rolled oats, high fibre.', 'OROATS1KG',              349.00,    399.00, '30000000-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/oats-1kg/640/480',                 'FarmFresh', 1100, 4.50,  84, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000030', 'Extra Virgin Olive Oil 500ml','Cold-pressed EVOO, glass bottle.', 'EVOO500ML',              899.00,    949.00, '30000000-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/evoo-500/640/480',                 'TerraNova',  920, 4.60,  51, NOW(), NOW()),
    -- Beauty
    ('20000000-0000-4000-8000-000000000031', 'Vitamin C Serum 30ml',       'Brightening serum with hyaluronic acid.', 'VTC30ML',            1499.00,   1799.00, '30000000-0000-4000-8000-000000000006', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/vitc-serum/640/480',               'GlowLab',    100, 4.50,  96, NOW(), NOW()),
    ('20000000-0000-4000-8000-000000000032', 'SPF 50 Sunscreen 50ml',      'Matte-finish broad spectrum SPF 50.', 'SPF50-50ML',            799.00,    899.00, '30000000-0000-4000-8000-000000000006', '11111111-1111-4111-8111-111111111111', 'ACTIVE',       'https://picsum.photos/seed/spf50/640/480',                    'GlowLab',     90, 4.40,  67, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Demo: every product sells for exactly 0.10
UPDATE products SET price = 0.10, original_price = 0.10;