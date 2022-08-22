package com.lucidworks.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SchemaV1ToV2 {
  public static String toCamelCase(String value) {
    StringBuilder sb = new StringBuilder();

    final char delimChar = '_';
    boolean upperCaseNextMatch = false;
    for (int charInd = 0; charInd < value.length(); ++charInd) {
      final char valueChar = value.charAt(charInd);
      if (charInd == 0) {
        sb.append(Character.toLowerCase(valueChar));
      } else if (valueChar == delimChar) {
        upperCaseNextMatch = true;
      } else if (upperCaseNextMatch) {
        sb.append(Character.toUpperCase(valueChar));
        upperCaseNextMatch = false;
      } else {
        sb.append(valueChar);
      }
    }

    return sb.toString();
  }

  public static void main(String[] args) throws Exception {
    File connectorV1SchemaJsonFile = new File(args[0]);
    ObjectMapper objectMapper = new ObjectMapper();
    Map connectorV1SchemaMap = objectMapper.readValue(connectorV1SchemaJsonFile, Map.class);

    Map mainDsProperties = (Map) connectorV1SchemaMap.get("properties");
    Map dsConfigurationProps = (Map) mainDsProperties.get("properties");

    List propertyGroups = (List) dsConfigurationProps.get("propertyGroups");
    Map<String, Set<String>> propGroupMap = new HashMap<>();
    for (Object propGroup : propertyGroups) {
      Map thisPg = (Map)propGroup;
      HashSet<String> fieldsInGroup = new HashSet<>();
      fieldsInGroup.addAll((List<String>) thisPg.get("properties"));
      propGroupMap.put((String) thisPg.get("label"), fieldsInGroup);
    }
    int order = 0;

    Map<String, List<String>> res = new HashMap<>();

    Map dsProperties = (Map) dsConfigurationProps.get("properties");
    for (Object nextProp : dsProperties.keySet()) {
      String nextPropStr = (String) nextProp;
      String group = "(no group)";
      for (Map.Entry<String, Set<String>> entry : propGroupMap.entrySet()) {
        if (entry.getValue().contains(nextPropStr)) {
          group = entry.getKey();
        }
      }
      if (!res.containsKey(group)) {
        res.put(group, new ArrayList<>());
      }
      Map nextPropMap = (Map) dsProperties.get(nextProp);
      String type = (String) nextPropMap.get("type");
      String title = (String) nextPropMap.get("title");
      String description = StringUtils.defaultString((String) nextPropMap.get("description"), "");
      Object defaultValue = nextPropMap.get("default");
      List hints = (List) nextPropMap.get("hints");

      String hintStr = ",\n        hints = { \"legacyAndaId:" + nextPropStr + "\"";
      if (hints != null) {
        for (Object nextHint : hints) {
          hintStr += ", UIHints." + (nextHint + "").toUpperCase(Locale.ROOT);
        }
      }
      hintStr += " }";
      String schemaType = "";
      String varType = "";
      if ("string".equals(type)) {
        schemaType = "@StringSchema(";
        if (defaultValue != null) {
          schemaType += "defaultValue = \"" + defaultValue + "\"";
        }
        schemaType += ")";
        varType = "String";
      } else if ("boolean".equals(type)) {
        schemaType = "@BooleanSchema()";
        varType = "Boolean";
      } else if ("integer".equals(type)) {
        schemaType = "@NumberSchema()";
        varType = "Integer";
      } else if ("array".equals(type)) {
        Map itemsMap = (Map) nextPropMap.get("items");
        String innerType = (String) itemsMap.get("type");
        Integer innerMinLength = (Integer) itemsMap.get("minLength");
        String innerSchemaType = "";
        String innerVarType = "";
        if ("boolean".equals(type)) {
          innerSchemaType = "@BooleanSchema()";
          innerVarType = "Boolean";
        } else if ("string".equals(innerType)) {
          innerSchemaType = "@StringSchema(";
          innerSchemaType += ")";
          innerVarType = "String";
        } else if ("integer".equals(type)) {
          innerSchemaType = "@NumberSchema(";
          innerSchemaType += ")";
          innerVarType = "Integer";
        }
        schemaType = "@ArraySchema()" + "\n    " + innerSchemaType;
        varType = "List<" + innerVarType + ">";
      }

      String cleanedFieldName = nextPropStr.replace("f.", "").replace("fs.", "");
      cleanedFieldName = toCamelCase(cleanedFieldName);
      String v2Prop = "\n    @Property(\n" +
          "        title = \"" + title + "\",\n" +
          "        description = \"" + description.replace("\"", "\\\"") + "\",\n" +
          "        order = " + (res.get(group).size() + 1) + hintStr + "\n" +
          "    )\n" +
          "    " + schemaType + "\n" +
          "    " + varType + " " + cleanedFieldName + "();";

      res.get(group).add(v2Prop);
    }

    for (String group : res.keySet()) {
      System.out.println("\n\n============== Group: " + group);
      System.out.println(StringUtils.join(res.get(group), "\n"));
    }
  }
}
