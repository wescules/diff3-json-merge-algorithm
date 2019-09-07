# diff3-json-merge-algorithm
Get diff of 2 json objects:
<code>getDifferences(String documentToUpdate, String currentDocument)</code>

Get the merged json object:
<code>merge(String sourceDocument, String currentDocument, String newDocument)</code>

This json merge algorthim uses the diff3 merging strategy which uses the diff between source and published as well as source and new to generate the final json. The Source json is a common ancestor of the new(latest changes) and the published (whats currently in storage) json.
