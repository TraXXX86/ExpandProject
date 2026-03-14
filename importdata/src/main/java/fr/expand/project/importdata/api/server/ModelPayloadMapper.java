package fr.expand.project.importdata.api.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.expand.project.importdata.model.ModelManager;
import fr.expand.project.importdata.model.generated.ATTRIBUTEDEFINITION;
import fr.expand.project.importdata.model.generated.ATTRIBUTEGROUP;
import fr.expand.project.importdata.model.generated.ATTRIBUTEREF;
import fr.expand.project.importdata.model.generated.DATAMODEL;
import fr.expand.project.importdata.model.generated.LABEL;
import fr.expand.project.importdata.model.generated.LABELS;
import fr.expand.project.importdata.model.generated.LANGUAGE;
import fr.expand.project.importdata.model.generated.LINKTYPE;
import fr.expand.project.importdata.model.generated.OBJECTTYPE;
import fr.expand.project.importdata.model.generated.TYPEREF;

final class ModelPayloadMapper {

    boolean isLinkTypeAllowed(ModelManager modelManager, LINKTYPE linkType, String candidateType, boolean source) {
        if (candidateType == null || candidateType.isBlank() || linkType == null) {
            return false;
        }

        List<TYPEREF> refs = null;
        if (source) {
            if (linkType.getSOURCETYPES() != null) {
                refs = linkType.getSOURCETYPES().getTYPEREF();
            }
        } else if (linkType.getTARGETTYPES() != null) {
            refs = linkType.getTARGETTYPES().getTYPEREF();
        }

        if (refs == null || refs.isEmpty()) {
            return false;
        }

        for (TYPEREF ref : refs) {
            if (ref == null || ref.getNAME() == null) {
                continue;
            }
            if (modelManager.isTypeOrSubtype(candidateType, ref.getNAME())) {
                return true;
            }
        }
        return false;
    }

    Map<String, Object> buildModelDetails(String modelKey, DATAMODEL model) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", modelKey);
        payload.put("name", model.getNAME());
        payload.put("version", model.getVERSION() == null ? "" : model.getVERSION());
        payload.put("defaultLanguage", model.getDEFAULTLANGUAGE() == null ? "" : model.getDEFAULTLANGUAGE());
        payload.put("userPortalLabels", extractLabels(model.getUSERPORTALLABELS()));

        List<Map<String, Object>> languages = new ArrayList<>();
        if (model.getLANGUAGES() != null && model.getLANGUAGES().getLANGUAGE() != null) {
            for (LANGUAGE language : model.getLANGUAGES().getLANGUAGE()) {
                Map<String, Object> row = new HashMap<>();
                row.put("code", language.getCODE() == null ? "" : language.getCODE());
                row.put("label", language.getLABEL() == null ? "" : language.getLABEL());
                languages.add(row);
            }
        }
        payload.put("languages", languages);

        List<Map<String, Object>> objectTypes = new ArrayList<>();
        if (model.getOBJECTTYPES() != null && model.getOBJECTTYPES().getOBJECTTYPE() != null) {
            int index = 0;
            for (OBJECTTYPE objectType : model.getOBJECTTYPES().getOBJECTTYPE()) {
                Map<String, Object> row = new HashMap<>();
                row.put("key", objectType.getNAME() + "-" + index++);
                row.put("name", objectType.getNAME());
                row.put("parent", objectType.getPARENT() == null ? "" : objectType.getPARENT());
                row.put("icon", objectType.getICON() == null ? "" : objectType.getICON());
                row.put("description", objectType.getDESCRIPTION() == null ? "" : objectType.getDESCRIPTION());

                List<Map<String, Object>> attributes = new ArrayList<>();
                if (objectType.getATTRIBUTEDEFINITIONS() != null
                    && objectType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION() != null) {
                    for (ATTRIBUTEDEFINITION attribute :
                        objectType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION()) {
                        Map<String, Object> attr = new HashMap<>();
                        attr.put("name", attribute.getNAME());
                        attr.put("type", attribute.getTYPE() == null ? "STRING" : attribute.getTYPE().value());
                        attr.put("required", attribute.isREQUIRED());
                        attr.put("defaultValue", attribute.getDEFAULTVALUE() == null ? "" : attribute.getDEFAULTVALUE());
                        attr.put("searchable", Boolean.TRUE.equals(attribute.isSEARCHABLE()));
                        attr.put("labels", extractLabels(attribute.getLABELS()));
                        attr.put("description", attribute.getDESCRIPTION() == null ? "" : attribute.getDESCRIPTION());
                        attributes.add(attr);
                    }
                }
                row.put("attributes", attributes);

                List<Map<String, Object>> representativeAttributes = new ArrayList<>();
                if (objectType.getREPRESENTATIVEATTRIBUTES() != null
                    && objectType.getREPRESENTATIVEATTRIBUTES().getATTRIBUTEREF() != null) {
                    int representativeIndex = 0;
                    for (ATTRIBUTEREF attributeRef : objectType.getREPRESENTATIVEATTRIBUTES().getATTRIBUTEREF()) {
                        Map<String, Object> refRow = new HashMap<>();
                        refRow.put("name", attributeRef.getNAME() == null ? "" : attributeRef.getNAME());
                        refRow.put("index", representativeIndex++);
                        if (attributeRef.getORDER() != null) {
                            refRow.put("order", attributeRef.getORDER());
                        }
                        representativeAttributes.add(refRow);
                    }
                }
                row.put("representativeAttributes", representativeAttributes);

                List<Map<String, Object>> attributeGroups = new ArrayList<>();
                if (objectType.getATTRIBUTEGROUPS() != null
                    && objectType.getATTRIBUTEGROUPS().getATTRIBUTEGROUP() != null) {
                    int groupIndex = 0;
                    for (ATTRIBUTEGROUP group : objectType.getATTRIBUTEGROUPS().getATTRIBUTEGROUP()) {
                        Map<String, Object> groupRow = new HashMap<>();
                        groupRow.put("key", (group.getNAME() == null ? "groupe" : group.getNAME()) + "-" + groupIndex++);
                        groupRow.put("name", group.getNAME() == null ? "" : group.getNAME());
                        if (group.getORDER() != null) {
                            groupRow.put("order", group.getORDER());
                        }

                        List<Map<String, Object>> groupAttributes = new ArrayList<>();
                        if (group.getATTRIBUTEREF() != null) {
                            int attributeIndex = 0;
                            for (ATTRIBUTEREF attributeRef : group.getATTRIBUTEREF()) {
                                Map<String, Object> refRow = new HashMap<>();
                                refRow.put("name", attributeRef.getNAME() == null ? "" : attributeRef.getNAME());
                                refRow.put("index", attributeIndex++);
                                if (attributeRef.getORDER() != null) {
                                    refRow.put("order", attributeRef.getORDER());
                                }
                                groupAttributes.add(refRow);
                            }
                        }
                        groupRow.put("attributes", groupAttributes);
                        attributeGroups.add(groupRow);
                    }
                }
                row.put("attributeGroups", attributeGroups);
                objectTypes.add(row);
            }
        }

        List<Map<String, Object>> linkTypes = new ArrayList<>();
        if (model.getLINKTYPES() != null && model.getLINKTYPES().getLINKTYPE() != null) {
            int index = 0;
            for (LINKTYPE linkType : model.getLINKTYPES().getLINKTYPE()) {
                Map<String, Object> row = new HashMap<>();
                row.put("key", linkType.getNAME() + "-" + index++);
                row.put("name", linkType.getNAME());
                row.put("directed", linkType.isDIRECTED());
                row.put("description", linkType.getDESCRIPTION() == null ? "" : linkType.getDESCRIPTION());
                row.put("labels", extractLabels(linkType.getLABELS()));
                row.put("sourceLabels", extractLabels(linkType.getSOURCELABELS()));
                row.put("targetLabels", extractLabels(linkType.getTARGETLABELS()));

                List<String> sources = new ArrayList<>();
                if (linkType.getSOURCETYPES() != null && linkType.getSOURCETYPES().getTYPEREF() != null) {
                    for (TYPEREF typeRef : linkType.getSOURCETYPES().getTYPEREF()) {
                        if (typeRef.getNAME() != null) {
                            sources.add(typeRef.getNAME());
                        }
                    }
                }

                List<String> targets = new ArrayList<>();
                if (linkType.getTARGETTYPES() != null && linkType.getTARGETTYPES().getTYPEREF() != null) {
                    for (TYPEREF typeRef : linkType.getTARGETTYPES().getTYPEREF()) {
                        if (typeRef.getNAME() != null) {
                            targets.add(typeRef.getNAME());
                        }
                    }
                }

                List<Map<String, Object>> attributes = new ArrayList<>();
                if (linkType.getATTRIBUTEDEFINITIONS() != null
                    && linkType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION() != null) {
                    for (ATTRIBUTEDEFINITION attribute :
                        linkType.getATTRIBUTEDEFINITIONS().getATTRIBUTEDEFINITION()) {
                        Map<String, Object> attr = new HashMap<>();
                        attr.put("name", attribute.getNAME());
                        attr.put("type", attribute.getTYPE() == null ? "STRING" : attribute.getTYPE().value());
                        attr.put("required", attribute.isREQUIRED());
                        attr.put("defaultValue", attribute.getDEFAULTVALUE() == null ? "" : attribute.getDEFAULTVALUE());
                        attr.put("searchable", Boolean.TRUE.equals(attribute.isSEARCHABLE()));
                        attr.put("labels", extractLabels(attribute.getLABELS()));
                        attr.put("description", attribute.getDESCRIPTION() == null ? "" : attribute.getDESCRIPTION());
                        attributes.add(attr);
                    }
                }

                row.put("sources", sources);
                row.put("targets", targets);
                row.put("attributes", attributes);
                linkTypes.add(row);
            }
        }

        payload.put("objectTypeCount", objectTypes.size());
        payload.put("linkTypeCount", linkTypes.size());
        payload.put("objectTypes", objectTypes);
        payload.put("linkTypes", linkTypes);
        return payload;
    }

    private Map<String, String> extractLabels(LABELS labelsNode) {
        Map<String, String> labels = new HashMap<>();
        if (labelsNode == null || labelsNode.getLABEL() == null) {
            return labels;
        }
        for (LABEL label : labelsNode.getLABEL()) {
            if (label.getLANGUAGE() != null && label.getVALUE() != null) {
                labels.put(label.getLANGUAGE(), label.getVALUE());
            }
        }
        return labels;
    }
}
