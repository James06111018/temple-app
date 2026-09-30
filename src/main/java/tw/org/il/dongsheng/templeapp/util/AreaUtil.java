package tw.org.il.dongsheng.templeapp.util;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AreaUtil {

    private static Map<String, List<String>> areaMap = new LinkedHashMap<>(); // 使用 LinkedHashMap 維護縣市排序

    public static Map<String, List<String>> getAllTaiwanAreas() {
        if(areaMap.isEmpty()) {
            initArea();
        }
        return areaMap;
    }

    public static String getZipCode(String areaText) {
        if (areaText == null || areaText.length() < 3) {
            return "";
        }
        String[] parts = areaText.split(" - ", 2);
        return parts.length == 2 ? parts[0] : "";
    }

    public static String getDistrictName(String areaText) {
        if (areaText == null) {
            return "";
        }
        String[] parts = areaText.split(" - ", 2);
        return parts.length == 2 ? parts[1] : areaText;
    }

    public static String normalizeDistrictName(String district) {
        return getDistrictName(district).replace("　", "").replace(" ", "");
    }

    public static String findAreaText(String city, String district) {
        if (city == null || district == null) {
            return null;
        }
        List<String> areas = getAllTaiwanAreas().get(city);
        if (areas == null) {
            return null;
        }
        return areas.stream()
                .filter(area -> getDistrictName(area).equals(district) || area.equals(district))
                .findFirst()
                .orElse(null);
    }

    public static String getAddressPrefix(String city, String areaText) {
        return normalizeCityName(city) + normalizeDistrictName(areaText);
    }

    public static String normalizeCityName(String city) {
        return city == null ? "" : city.replace("臺", "台");
    }

    /** 將舊資料常見的「台／臺」差異轉成下拉選單實際使用的縣市名稱。 */
    public static String resolveCityName(String city) {
        if (city == null || city.isBlank()) {
            return "";
        }
        String normalized = normalizeCityName(city).replace("　", "").replace(" ", "");
        return getAllTaiwanAreas().keySet().stream()
                .filter(candidate -> normalizeCityName(candidate).equals(normalized))
                .findFirst()
                .orElse(city.trim());
    }

    /** 從已包含完整行政區的舊地址找出縣市，供 city 空白的信眾補顯示與下次儲存。 */
    public static String findCityFromAddress(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String normalizedAddress = normalizeAddress(address);
        return getAllTaiwanAreas().keySet().stream()
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .filter(city -> normalizedAddress.startsWith(normalizeAddress(city)))
                .findFirst()
                .orElse("");
    }

    public static String findDistrictFromAddress(String city, String address) {
        String resolvedCity = resolveCityName(city);
        if (resolvedCity.isBlank() || address == null || address.isBlank()) {
            return "";
        }
        String remainder = normalizeAddress(address);
        String normalizedCity = normalizeAddress(resolvedCity);
        if (remainder.startsWith(normalizedCity)) {
            remainder = remainder.substring(normalizedCity.length());
        }
        final String remainingAddress = remainder;
        return getAllTaiwanAreas().getOrDefault(resolvedCity, List.of()).stream()
                .map(AreaUtil::getDistrictName)
                .sorted((left, right) -> Integer.compare(right.length(), left.length()))
                .filter(district -> remainingAddress.startsWith(normalizeAddress(district)))
                .findFirst()
                .orElse("");
    }

    private static String normalizeAddress(String value) {
        return normalizeCityName(value).replace("　", "").replace(" ", "");
    }

    private static void initArea() {
        areaMap.put("臺北市", Arrays.asList("100 - 中正區", "103 - 大同區", "104 - 中山區", "108 - 萬華區", "110 - 信義區", "105 - 松山區", "106 - 大安區", "115 - 南港區", "112 - 北投區", "114 - 內湖區", "111 - 士林區", "116 - 文山區"));
        areaMap.put("新北市", Arrays.asList("220 - 板橋區", "241 - 三重區", "235 - 中和區", "234 - 永和區", "242 - 新莊區", "231 - 新店區", "236 - 土城區", "247 - 蘆洲區", "221 - 汐止區", "238 - 樹林區", "251 - 淡水區", "239 - 鶯歌區", "237 - 三峽區", "224 - 瑞芳區", "248 - 五股區", "243 - 泰山區", "244 - 林口區", "222 - 深坑區", "223 - 石碇區", "232 - 坪林區", "252 - 三芝區", "253 - 石門區", "249 - 八里區", "226 - 平溪區", "227 - 雙溪區", "228 - 貢寮區", "208 - 金山區", "207 - 萬里區", "233 - 烏來區"));
        areaMap.put("桃園市", Arrays.asList("330 - 桃園區", "320 - 中壢區", "335 - 大溪區", "326 - 楊梅區", "338 - 蘆竹區", "337 - 大園區", "333 - 龜山區", "334 - 八德區", "325 - 龍潭區", "324 - 平鎮區", "327 - 新屋區", "328 - 觀音區", "336 - 復興區"));
        areaMap.put("臺中市", Arrays.asList("400 - 中區", "401 - 東區", "402 - 南區", "403 - 西區", "404 - 北區", "406 - 北屯區", "407 - 西屯區", "408 - 南屯區", "411 - 太平區", "412 - 大里區", "413 - 霧峰區", "414 - 烏日區", "420 - 豐原區", "421 - 后里區", "423 - 東勢區", "422 - 石岡區", "426 - 新社區", "424 - 和平區", "429 - 神岡區", "427 - 潭子區", "428 - 大雅區", "432 - 大肚區", "434 - 龍井區", "433 - 沙鹿區", "435 - 梧棲區", "436 - 清水區", "437 - 大甲區", "438 - 外埔區", "439 - 大安區"));
        areaMap.put("臺南市", Arrays.asList("700 - 中西區", "701 - 東區", "702 - 南區", "704 - 北區", "708 - 安平區", "709 - 安南區", "710 - 永康區", "711 - 歸仁區", "712 - 新化區", "713 - 左鎮區", "714 - 玉井區", "715 - 楠西區", "716 - 南化區", "717 - 仁德區", "718 - 關廟區", "719 - 龍崎區", "720 - 官田區", "721 - 麻豆區", "722 - 佳里區", "723 - 西港區", "724 - 七股區", "725 - 將軍區", "726 - 學甲區", "727 - 北門區", "730 - 新營區", "731 - 後壁區", "732 - 白河區", "733 - 東山區", "734 - 六甲區", "735 - 下營區", "736 - 柳營區", "737 - 鹽水區", "741 - 善化區", "742 - 大內區", "743 - 山上區", "744 - 新市區", "745 - 安定區"));
        areaMap.put("高雄市", Arrays.asList("811 - 楠梓區", "813 - 左營區", "804 - 鼓山區", "807 - 三民區", "803 - 鹽埕區", "801 - 前金區", "800 - 新興區", "802 - 苓雅區", "806 - 前鎮區", "805 - 旗津區", "812 - 小港區", "830 - 鳳山區", "831 - 大寮區", "833 - 鳥松區", "832 - 林園區", "814 - 仁武區", "840 - 大樹區", "815 - 大社區", "820 - 岡山區", "821 - 路竹區", "822 - 阿蓮區", "823 - 田寮區", "824 - 燕巢區", "825 - 橋頭區", "826 - 梓官區", "827 - 彌陀區", "828 - 永安區", "829 - 湖內區", "842 - 旗山區", "843 - 美濃區", "844 - 六龜區", "845 - 內門區", "847 - 甲仙區", "848 - 桃源區", "849 - 那瑪夏區", "851 - 茂林區", "846 - 杉林區"));
        areaMap.put("基隆市", Arrays.asList("200 - 仁愛區", "201 - 信義區", "202 - 中正區", "203 - 中山區", "204 - 安樂區", "205 - 暖暖區", "206 - 七堵區"));
        areaMap.put("新竹市", Arrays.asList("300 - 東區", "300 - 北區", "300 - 香山區"));
        areaMap.put("嘉義市", Arrays.asList("600 - 東區", "600 - 西區"));
        areaMap.put("新竹縣", Arrays.asList("302 - 竹北市", "310 - 竹東鎮", "305 - 新埔鎮", "306 - 關西鎮", "303 - 湖口鄉", "304 - 新豐鄉", "307 - 芎林鄉", "312 - 橫山鄉", "314 - 北埔鄉", "308 - 寶山鄉", "315 - 峨眉鄉", "313 - 尖石鄉", "311 - 五峰鄉"));
        areaMap.put("苗栗縣", Arrays.asList("360 - 苗栗市", "351 - 頭份市", "350 - 竹南鎮", "356 - 後龍鎮", "357 - 通霄鎮", "358 - 苑裡鎮", "369 - 卓蘭鎮", "361 - 造橋鄉", "368 - 西湖鄉", "362 - 頭屋鄉", "363 - 公館鄉", "366 - 銅鑼鄉", "367 - 三義鄉", "364 - 大湖鄉", "354 - 獅潭鄉", "352 - 三灣鄉", "353 - 南庄鄉", "365 - 泰安鄉"));
        areaMap.put("彰化縣", Arrays.asList("500 - 彰化市", "510 - 員林市", "508 - 和美鎮", "505 - 鹿港鎮", "514 - 溪湖鎮", "526 - 二林鎮", "520 - 田中鎮", "521 - 北斗鎮", "503 - 花壇鄉", "502 - 芬園鄉", "515 - 大村鄉", "512 - 永靖鄉", "509 - 伸港鄉", "507 - 線西鄉", "506 - 福興鄉", "504 - 秀水鄉", "516 - 埔鹽鄉", "513 - 埔心鄉", "527 - 大城鄉", "525 - 竹塘鄉", "523 - 埤頭鄉", "524 - 溪州鄉", "530 - 二水鄉", "511 - 社頭鄉"));
        areaMap.put("南投縣", Arrays.asList("540 - 南投市", "545 - 埔里鎮", "542 - 草屯鎮", "557 - 竹山鎮", "552 - 集集鎮", "551 - 名間鄉", "558 - 鹿谷鄉", "541 - 中寮鄉", "555 - 魚池鄉", "544 - 國姓鄉", "553 - 水里鄉", "556 - 信義鄉", "546 - 仁愛鄉"));
        areaMap.put("雲林縣", Arrays.asList("640 - 斗六市", "630 - 斗南鎮", "632 - 虎尾鎮", "648 - 西螺鎮", "633 - 土庫鎮", "651 - 北港鎮", "646 - 古坑鄉", "631 - 大埤鄉", "647 - 莿桐鄉", "643 - 林內鄉", "649 - 二崙鄉", "637 - 崙背鄉", "638 - 麥寮鄉", "635 - 東勢鄉", "634 - 褒忠鄉", "636 - 臺西鄉", "655 - 元長鄉", "654 - 四湖鄉", "653 - 口湖鄉", "652 - 水林鄉"));
        areaMap.put("嘉義縣", Arrays.asList("612 - 太保市", "613 - 朴子市", "625 - 布袋鎮", "622 - 大林鎮", "621 - 民雄鄉", "623 - 溪口鄉", "616 - 新港鄉", "615 - 六腳鄉", "614 - 東石鄉", "624 - 義竹鄉", "611 - 鹿草鄉", "608 - 水上鄉", "606 - 中埔鄉", "604 - 竹崎鄉", "603 - 梅山鄉", "602 - 番路鄉", "607 - 大埔鄉", "605 - 阿里山鄉"));
        areaMap.put("屏東縣", Arrays.asList("900 - 屏東市", "920 - 潮州鎮", "928 - 東港鎮", "946 - 恆春鎮", "913 - 萬丹鄉", "908 - 長治鄉", "909 - 麟洛鄉", "904 - 九如鄉", "905 - 里港鄉", "906 - 高樹鄉", "907 - 鹽埔鄉", "912 - 內埔鄉", "911 - 竹田鄉", "923 - 萬巒鄉", "932 - 新園鄉", "924 - 崁頂鄉", "927 - 林邊鄉", "926 - 南州鄉", "931 - 佳冬鄉", "929 - 琉球鄉", "944 - 車城鄉", "947 - 滿州鄉", "940 - 枋寮鄉", "941 - 枋山鄉", "902 - 霧臺鄉", "903 - 瑪家鄉", "921 - 泰武鄉", "922 - 來義鄉", "942 - 春日鄉", "943 - 獅子鄉", "945 - 牡丹鄉", "901 - 三地門鄉"));
        areaMap.put("宜蘭縣", Arrays.asList("260 - 宜蘭市", "265 - 羅東鎮", "270 - 蘇澳鎮", "261 - 頭城鎮", "262 - 礁溪鄉", "263 - 壯圍鄉", "264 - 員山鄉", "269 - 冬山鄉", "268 - 五結鄉", "266 - 三星鄉", "267 - 大同鄉", "272 - 南澳鄉"));
        areaMap.put("花蓮縣", Arrays.asList("970 - 花蓮市", "975 - 鳳林鎮", "981 - 玉里鎮", "971 - 新城鄉", "973 - 吉安鄉", "974 - 壽豐鄉", "976 - 光復鄉", "977 - 豐濱鄉", "978 - 瑞穗鄉", "983 - 富里鄉", "972 - 秀林鄉", "979 - 萬榮鄉", "982 - 卓溪鄉"));
        areaMap.put("臺東縣", Arrays.asList("950 - 臺東市", "961 - 成功鎮", "956 - 關山鎮", "954 - 卑南鄉", "965 - 大武鄉", "963 - 太麻里鄉", "959 - 東河鄉", "962 - 長濱鄉", "955 - 鹿野鄉", "958 - 池上鄉", "951 - 綠島鄉", "953 - 延平鄉", "957 - 海端鄉", "966 - 達仁鄉", "964 - 金峰鄉", "952 - 蘭嶼鄉"));
        areaMap.put("澎湖縣", Arrays.asList("880 - 馬公市", "885 - 湖西鄉", "884 - 白沙鄉", "881 - 西嶼鄉", "882 - 望安鄉", "883 - 七美鄉"));
        areaMap.put("金門縣", Arrays.asList("893 - 金城鎮", "891 - 金湖鎮", "890 - 金沙鎮", "892 - 金寧鄉", "894 - 烈嶼鄉", "896 - 烏坵鄉"));
        areaMap.put("連江縣", Arrays.asList("209 - 南竿鄉", "210 - 北竿鄉", "211 - 莒光鄉", "212 - 東引鄉"));
    }
}
