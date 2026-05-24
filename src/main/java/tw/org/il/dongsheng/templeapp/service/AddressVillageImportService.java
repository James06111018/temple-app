package tw.org.il.dongsheng.templeapp.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tw.org.il.dongsheng.templeapp.model.AddressVillage;
import tw.org.il.dongsheng.templeapp.repository.AddressRepository;
import tw.org.il.dongsheng.templeapp.util.AreaUtil;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class AddressVillageImportService {
    private static final String DEFAULT_API_BASE = "https://api.nlsc.gov.tw/other";

    private final AddressRepository repository;

    public AddressVillageImportService(AddressRepository repository) {
        this.repository = repository;
    }

    public int importFromNlsc() throws Exception {
        return importFromNlsc(URI.create(DEFAULT_API_BASE));
    }

    public int importFromNlsc(URI apiBase) throws Exception {
        List<AddressVillage> villages = new ArrayList<>();
        for (County county : loadCounties(apiBase)) {
            for (Town town : loadTowns(apiBase, county.code())) {
                for (String village : loadVillages(apiBase, county.code(), town.code())) {
                    villages.add(new AddressVillage(
                            null,
                            AreaUtil.normalizeCityName(county.name()),
                            AreaUtil.normalizeDistrictName(town.name()),
                            village
                    ));
                }
            }
        }
        return repository.replaceVillages(villages);
    }

    private List<County> loadCounties(URI apiBase) throws Exception {
        Document document = loadXml(apiBase.resolve(apiBase.getPath() + "/ListCounty"));
        NodeList nodes = document.getElementsByTagName("countyItem");
        List<County> counties = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            counties.add(new County(text(element, "countycode"), text(element, "countyname")));
        }
        return counties;
    }

    private List<Town> loadTowns(URI apiBase, String countyCode) throws Exception {
        Document document = loadXml(apiBase.resolve(apiBase.getPath() + "/ListTown/" + countyCode));
        NodeList nodes = document.getElementsByTagName("townItem");
        List<Town> towns = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            towns.add(new Town(text(element, "towncode"), text(element, "townname")));
        }
        return towns;
    }

    private List<String> loadVillages(URI apiBase, String countyCode, String townCode) throws Exception {
        Document document = loadXml(apiBase.resolve(apiBase.getPath() + "/ListVillage/" + countyCode + "/" + townCode));
        NodeList nodes = document.getElementsByTagName("village");
        List<String> villages = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String village = text((Element) nodes.item(i), "villageName");
            if (!village.isBlank()) {
                villages.add(village);
            }
        }
        return villages;
    }

    private Document loadXml(URI uri) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream inputStream = uri.toURL().openStream()) {
            return factory.newDocumentBuilder().parse(inputStream);
        }
    }

    private String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private record County(String code, String name) {
    }

    private record Town(String code, String name) {
    }
}
