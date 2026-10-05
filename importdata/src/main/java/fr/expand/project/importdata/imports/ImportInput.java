package fr.expand.project.importdata.imports;

import fr.expand.project.importdata.dto.generated.*;
import fr.expand.project.importdata.xml.XmlSupport;

import java.util.*;

public record ImportInput(List<Entry> entries) {
    public record Identity(String type, int dataId) {
        public String key() {
            return type + "/" + dataId;
        }
    }

    public record Entry(
            int row,
            String entityType,
            String type,
            Integer dataId,
            Identity from,
            Identity to,
            Map<String, Object> attributes,
            List<String> errors) {}

    public static ImportInput table(
            TabularFile.Table table, String type, String idColumn, Map<String, String> mapping) {
        if (type == null || type.isBlank())
            throw new IllegalArgumentException("Type d'objet requis");
        int idIndex = table.columns().indexOf(idColumn);
        if (idIndex < 0) throw new IllegalArgumentException("Colonne d'identifiant stable requise");
        if (mapping == null || mapping.isEmpty())
            throw new IllegalArgumentException("Correspondance des attributs requise");
        for (String column : mapping.values())
            if (!table.columns().contains(column))
                throw new IllegalArgumentException("Colonne inconnue : " + column);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < table.rows().size(); i++) {
            var values = table.rows().get(i);
            Integer id = null;
            List<String> errors = new ArrayList<>();
            try {
                id = Integer.valueOf(values.get(idIndex).trim());
                if (id < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                errors.add("Identifiant entier positif ou nul requis");
            }
            Map<String, Object> attrs = new TreeMap<>();
            mapping.forEach(
                    (key, column) -> attrs.put(key, values.get(table.columns().indexOf(column))));
            entries.add(
                    new Entry(
                            table.rowNumbers().get(i),
                            "OBJECT",
                            type,
                            id,
                            null,
                            null,
                            attrs,
                            errors));
        }
        return new ImportInput(entries);
    }

    public static ImportInput xml(String xml) throws jakarta.xml.bind.JAXBException {
        DATAS data = XmlSupport.parseData(xml);
        List<Entry> entries = new ArrayList<>();
        if (data.getOBJECTS() != null) {
            int row = 0;
            for (OBJECT object : data.getOBJECTS().getOBJECT()) {
                List<String> errors = new ArrayList<>();
                if (object.getID() < 0) errors.add("Identifiant entier positif ou nul requis");
                entries.add(
                        new Entry(
                                ++row,
                                "OBJECT",
                                object.getTYPE(),
                                object.getID(),
                                null,
                                null,
                                attrs(object.getATTRIBUTE(), errors),
                                errors));
            }
        }
        if (data.getLINKS() != null) {
            int row = 0;
            for (LINK link : data.getLINKS().getLINK()) {
                List<String> errors = new ArrayList<>();
                entries.add(
                        new Entry(
                                ++row,
                                "LINK",
                                link.getTYPE(),
                                null,
                                new Identity(
                                        link.getOBJLINKA().getTYPE(), link.getOBJLINKA().getID()),
                                new Identity(
                                        link.getOBJLINKB().getTYPE(), link.getOBJLINKB().getID()),
                                attrs(link.getATTRIBUTE(), errors),
                                errors));
            }
        }
        if (entries.size() > TabularFile.MAX_ROWS)
            throw new IllegalArgumentException("Maximum 5 000 objets et liens par import");
        return new ImportInput(entries);
    }

    private static Map<String, Object> attrs(List<ATTRIBUTE> attributes, List<String> errors) {
        Map<String, Object> result = new TreeMap<>();
        for (ATTRIBUTE attr : attributes)
            if (result.put(attr.getKEY(), Objects.toString(attr.getVALUE(), "")) != null)
                errors.add("Attribut dupliqué : " + attr.getKEY());
        return result;
    }
}
