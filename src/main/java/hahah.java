import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flipkart.zjsonpatch.DiffFlags;
import com.flipkart.zjsonpatch.JsonDiff;
import com.flipkart.zjsonpatch.JsonPatch;
import com.flipkart.zjsonpatch.JsonPatchApplicationException;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.CollectionUtils;

import static java.lang.System.currentTimeMillis;

public class hahah {
    private ObjectMapper objectMapper = new ObjectMapper();
    private static List<String> PATHS_TO_IGNORE = null;
    EnumSet<DiffFlags> flags = DiffFlags.dontNormalizeOpIntoMoveAndCopy();
    public hahah() {
        initializePathsToIgnore();
    }

    private void initializePathsToIgnore() {
        if (PATHS_TO_IGNORE != null) {
            return;
        }
        try {
        	DataInputStream inputStream = new DataInputStream(new FileInputStream("pathsToIgnoreWhileDiff.txt"));
        } catch (Exception e) {
        	System.out.println("Error initializing pathsToIgnore");
        }

        if (PATHS_TO_IGNORE == null) {
            throw new RuntimeException("Unable to read files to check pathsToIgnore");
        }
    }

    public String getDifferences(String documentToUpdate, String currentDocument) {
        try {
            JsonNode beforeNode = objectMapper.readTree(objectMapper.writeValueAsBytes(currentDocument));
            JsonNode afterNode = objectMapper.readTree(objectMapper.writeValueAsBytes(documentToUpdate));
            JsonNode patchNode = JsonDiff.asJson(beforeNode, afterNode, flags);

            StringBuilder comment = new StringBuilder();

            for (int i = 0; i < patchNode.size(); i++) {
                final int tempIndexForLambda = i;
                if (PATHS_TO_IGNORE.stream().filter(tag -> patchNode.get(tempIndexForLambda).get("path").toString().contains(tag)).count() > 0) {
                    continue;
                } else {
                    String operation = patchNode.get(i).get("op").toString();
                    String pathName = patchNode.get(i).get("path").toString();
                    String value = patchNode.get(i).get("value").toString();

                    if (operation.contains("replace")) {
                        comment.append("Updated " + pathName + " to " + value + "\n");
                        //System.out.println("Updated " + pathName + " to " +value);
                    } else if (operation.contains("remove")) {
                        comment.append("Removed " + value + " from " + pathName + "\n");
                        //System.out.println("Removed " + value + " from " + pathName);
                    } else if (operation.contains("add")) {
                        comment.append("Added " + value + " to " + pathName + "\n");
                        //System.out.println("Added " + value + " to " + pathName);
                    }

                }
            }

            return comment.toString();
        } catch (IOException e) {
        	System.out.println("Exception is: " + e);
        }

        return null;
    }

    public String merge(String sourceDocument, String currentDocument, String newDocument) throws Exception  {
        JsonNode sourceNode = objectMapper.readTree(objectMapper.writeValueAsBytes(sourceDocument));
        JsonNode currentNode = objectMapper.readTree(objectMapper.writeValueAsBytes(currentDocument));
        JsonNode newNode = objectMapper.readTree(objectMapper.writeValueAsBytes(newDocument));
        JsonNode mergedNode = merge(sourceNode, currentNode, newNode);
        if (mergedNode == null) {
            throw new Exception("Cannot merge nodes");
        }
        return (String) objectMapper.readValue(objectMapper.writeValueAsString(mergedNode), sourceDocument.getClass());
    }

    /**
     * @param sourceNode  sourceNode for both current and new node
     * @param currentNode current node in repository
     * @param newNode     new Node that user wants to save
     * @throws IOException
     */
    private JsonNode merge(JsonNode sourceNode, JsonNode currentNode, JsonNode newNode) throws IOException {
        JsonNode diff1 = JsonDiff.asJson(sourceNode, newNode, flags);
        JsonNode diff2 = JsonDiff.asJson(sourceNode, currentNode, flags);

        if (isNoMergeConflict(diff1, diff2, currentNode, newNode)) {
            String result = concateDiffs(diff1, diff2);
            JsonNode diffs = createSetofDiffs(objectMapper.readTree(result));
            System.out.println("fin " + diffs);
            return (applyPatch(sourceNode, diffs));
        }

        return null;
    }

    private String concateDiffs(JsonNode diff1, JsonNode diff2) {
        String str1 = diff1.toString().substring(1, diff1.toString().length() - 1);
        String str2 = diff2.toString().substring(1, diff2.toString().length() - 1);

        List<String> strs = Arrays.asList(str1, str2);
        String result = "[";

        for(String str : strs){
            if(str != null && str.length() > 0){
                result += str + ",";
            }
        }
        result = result.substring(0, result.length()-1) + "]";
        return result;
    }

    private String removeIndexFromPath(String path) {
        int index = getArrayIndexFromPath(path.substring(1, path.length()-1));
        return index == - 1 ? path : path.substring(0, path.length()-Integer.toString(index).length()-1);
    }

    private JsonNode createSetofDiffs(JsonNode diffs) throws IOException {
        String stringOfDiffs = "";
        Map<String, JsonNode> map = new HashMap<>();
        for (JsonNode diff : diffs) {
            String value = diff.get("value").toString();
            //dont add to set if the (value and path) exist as a combo
            if (map.containsKey(value) && isSamePath(map, diff, value)) {
                continue;
            } else {
                if(value.contains("delete")){
                    continue;
                }
                map.put(value, diff);
                stringOfDiffs += diff.toString() + ",";
            }
        }
        stringOfDiffs = "[" + stringOfDiffs.substring(0, stringOfDiffs.length() - 1) + "]";
        return objectMapper.readTree(stringOfDiffs);
    }

    private JsonNode applyPatch(JsonNode currentNode, JsonNode diff1) {
        JsonNode result = null;
        long startTime = currentTimeMillis();
        try {
            result = JsonPatch.apply(diff1, currentNode);
        } catch (JsonPatchApplicationException e) {
        	System.out.println("Unable to apply patch" + e);
        } finally {
            System.out.println("Time taken to apply patch: "+(currentTimeMillis() - startTime)+"}, patch applied: "+result != null+"");
        }
        return result;
    }


    private int getArrayIndexFromPath(String path) {
        Matcher matcher = Pattern.compile("(\\d+$)").matcher(path);
        String index = "";
        if(matcher.find()){
            index = matcher.group(0);
        }
        try {
            return Integer.parseInt(index);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Boolean isPathAnArray(String path){
        return path.matches(".*?\\/\\d+$");
    }

    private void modifyPathForArrays(HashMap<String, JsonNode> diff1Map, HashMap<String, JsonNode> diff2Map, JsonNode diff1, JsonNode diff2, JsonNode currentNode, JsonNode newNode) {
        HashMap<String, Integer> maxIndexForPath = new HashMap<>();

        //init path frequency map (path, maxIndexForPath)
        getMaxIndexFromPath(diff1Map, diff1, maxIndexForPath);
        getMaxIndexFromPath(diff2Map, diff2, maxIndexForPath);

        for (Map.Entry<String, JsonNode> entry : diff1Map.entrySet()) {
            String path  = entry.getValue().get("path").asText();
            String operation = entry.getValue().get("op").toString();
            String pathLiteral = entry.getKey().substring(1, entry.getKey().length() - 1);
            if(isSameValidationRule(diff2Map, path) && path.contains("validationRules")){
                throw new RuntimeException("Validation rule: data loss");
            }

            if (isSamePath(diff2Map, entry.getKey()) && isDifferentValue(diff2Map, entry) && operation.contains("replace")){
                if(!path.contains("routingMessageContext") && !isPathAnArray(pathLiteral)){
                    continue;
                }

                modifyReplaceWithAddOperation(diff1Map, diff2Map, maxIndexForPath, entry);
                //System.out.println(diff2Map.get(entry.getKey()));
                continue;
            }

            //if its an add/remove operation, paths match, and the value is different
            if (isSamePath(diff2Map, entry.getKey()) && isDifferentValue(diff2Map, entry) && (operation.contains("add") || operation.contains("remove"))) {
                if(isPathAnArray(path) && path.contains("validationRules") && operation.contains("remove")){
                    throw new RuntimeException("Validation rule: data loss");
                }

                modifyAddOperation(diff2Map, maxIndexForPath, entry);
                //System.out.println(diff2Map.get(entry.getKey()));
            }
        }
    }

    private void modifyReplaceWithAddOperation(HashMap<String, JsonNode> diff1Map, HashMap<String, JsonNode> diff2Map, HashMap<String, Integer> maxIndexForPath, Map.Entry<String, JsonNode> entry) {
            int maxInArray = maxIndexForPath.get(removeIndexFromPath(entry.getKey())) + 1;
            ((ObjectNode) diff1Map.get(entry.getKey())).put("op", "add");
            ((ObjectNode) diff1Map.get(entry.getKey())).put("path", removeIndexFromPath(entry.getKey().substring(1, entry.getKey().length() - 2)) + maxInArray);
            ((ObjectNode) diff1Map.get(entry.getKey())).put("value", diff2Map.get(entry.getKey()).get("value"));
            maxIndexForPath.put(removeIndexFromPath(entry.getKey()), maxIndexForPath.get(removeIndexFromPath(entry.getKey())) + 1);
    }

    private void modifyAddOperation(HashMap<String, JsonNode> diff2Map, HashMap<String, Integer> maxIndexForPath, Map.Entry<String, JsonNode> entry) {
        int maxInArray = maxIndexForPath.get(removeIndexFromPath(entry.getKey())) + 1;
        ((ObjectNode) diff2Map.get(entry.getKey())).put("path", removeIndexFromPath(entry.getKey().substring(1, entry.getKey().length() - 2)) + maxInArray);
        maxIndexForPath.put(removeIndexFromPath(entry.getKey()), maxIndexForPath.get(removeIndexFromPath(entry.getKey())) + 1);
    }

    private boolean isDifferentValue(HashMap<String, JsonNode> diff2Map, Map.Entry<String, JsonNode> entry) {
        return entry.getValue().get("value") != null
                && diff2Map.get(entry.getKey()).get("value") != null
                && entry.getValue().get("value").toString() != diff2Map.get(entry.getKey()).get("value").toString() ;
    }

    private boolean isSamePath(HashMap<String, JsonNode> diffMap, String value) {
        return diffMap.containsKey(value);
    }
    private boolean isSameValidationRule(HashMap<String, JsonNode> diff2Map, String path) {
        for(String path2 : diff2Map.keySet()){
            if(path2.contains("validationRules") && isSameValidationRule(path, path2)){
                return true;
            }
        }
        return false;
    }

    private boolean isSameValidationRule(String path1, String path2) {
        Matcher matcher1 = Pattern.compile("(\\d+\\/validationRules)").matcher(path1);
        Matcher matcher2 = Pattern.compile("(\\d+\\/validationRules)").matcher(path2);

        if(matcher1.find()){
            path1 = matcher1.group(0);
        }
        if(matcher2.find()){
            path2 = matcher2.group(0);
        }

        return path1.contains(path2);
    }

    private boolean isSamePath(Map<String, JsonNode> map, JsonNode diff, String value) {
        return removeIndexFromPath(map.get(value).get("path").toString()).contains(removeIndexFromPath(diff.get("path").toString()));
    }

    private void getMaxIndexFromPath(HashMap<String, JsonNode> diffMap, JsonNode diff, HashMap<String, Integer> count) {
        for (int i = 0; i < diff.size(); i++) {
            String path  = diff.get(i).get("path").toString();

            diffMap.put(path, diff.get(i));

            int index = getArrayIndexFromPath(diff.get(i).get("path").asText());
            if (count.containsKey(removeIndexFromPath(path))) {
                count.put(removeIndexFromPath(path), Math.max(count.get(removeIndexFromPath(path)), index));
            } else {
                count.put(removeIndexFromPath(path), index);
            }
        }
    }

    private boolean isNoMergeConflict(JsonNode diff1, JsonNode diff2, JsonNode currentNode, JsonNode newNode) {
        System.out.println("**********************");
        System.out.println("Pub Changes: " + diff2);
        System.out.println("New Changes: " + diff1);
        HashMap<String, JsonNode> diff1Map = new HashMap<>();
        HashMap<String, JsonNode> diff2Map = new HashMap<>();
        modifyPathForArrays(diff1Map, diff2Map, diff1, diff2, currentNode, newNode);

        long startTime = currentTimeMillis();
        Map<String, JsonNode> paths1 = new HashMap();
        Map<String, JsonNode> paths2 = new HashMap();
        addAllPaths(diff1, paths1);
        addAllPaths(diff2, paths2);
        Collection<String> intersection = CollectionUtils.intersection(paths1.keySet(), paths2.keySet());
        removeCurrentBranchVersion(intersection);
        removePathsWithSameValues(intersection, paths1, paths2);
        System.out.println("Time taken to determine if there is no conflict: "+(currentTimeMillis() - startTime)+", conflict found: "+!intersection.isEmpty()+", common changes: " + intersection+")");
        if(!intersection.isEmpty()){
            throw new RuntimeException(intersection.toString());
        }
        return intersection.isEmpty();
    }

    private void removePathsWithSameValues(Collection<String> intersection, Map<String, JsonNode> paths1, Map<String, JsonNode> paths2) {
        intersection.removeIf(path -> hasSameValueInDiffs(paths1.get(path), paths2.get(path)));
    }

    private boolean hasSameValueInDiffs(JsonNode diff1, JsonNode diff2) {
        if (diff1 == null && diff2 == null) return true;
        if (diff1 == null || diff2 == null) return false;
        return diff1.equals(diff2);
    }

    private void removeCurrentBranchVersion(Collection<String> intersection) {
        intersection.removeAll(PATHS_TO_IGNORE);
    }

    private void addAllPaths(JsonNode diff1, Map<String, JsonNode> paths) {
        for (JsonNode jsonNode : diff1) {
            if (jsonNode.has("path")) {
                paths.put(jsonNode.get("op").asText() + jsonNode.get("path").asText(), jsonNode);
            }
            addAllPaths(jsonNode, paths);
        }
    }

    private void addAllPathsWithoutOpertation(JsonNode diff1, Map<String, JsonNode> paths) {
        for (JsonNode jsonNode : diff1) {
            if (jsonNode.has("path")) {
                paths.put(jsonNode.get("path").asText(), jsonNode);
            }
            addAllPaths(jsonNode, paths);
        }
    }

    public boolean hasChanged(Object oldRule, Object newRule, List<String> pathsToIgnore, List<Pattern> regexPaths) {
        try {
            JsonNode oldNode = objectMapper.readTree(objectMapper.writeValueAsBytes(oldRule));
            JsonNode newNode = objectMapper.readTree(objectMapper.writeValueAsBytes(newRule));
            JsonNode diff = JsonDiff.asJson(oldNode, newNode);
            Map<String, JsonNode> paths = new HashMap<>();
            addAllPathsWithoutOpertation(diff, paths);
            removeAllPathsToIgnore(paths.keySet(), pathsToIgnore, regexPaths);

            return !paths.isEmpty();
        } catch (IOException e) {
            throw new RuntimeException("Unable to determine whether changes require wip", e);
        }

    }

    private void removeAllPathsToIgnore(Set<String> paths, List<String> pathsToIgnore, List<Pattern> regexPaths) {
        paths.removeAll(pathsToIgnore);
        paths.removeAll(PATHS_TO_IGNORE);
        regexPaths.forEach(regex -> removePathMatchingRegex(paths, regex));
    }

    private void removePathMatchingRegex(Set<String> paths, Pattern regex) {
        paths.removeIf(path -> regex.matcher(path).matches());
    }
    
    public static void main(String args[]) 
    { 
        
    } 

}









