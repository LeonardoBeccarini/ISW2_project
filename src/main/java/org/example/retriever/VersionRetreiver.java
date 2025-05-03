package org.example.retriever;

import org.example.Utils.JsonUtils;
import org.example.model.Version;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class VersionRetreiver {
    private List<Version> versionList;

    public VersionRetreiver(String projectName) {
        try {
            getVersions(projectName);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    public List<Version> getAffectedVersions(JSONArray versions) throws JSONException {
        List<Version> affectedVersions = new ArrayList<>();
        for (int i = 0; i < versions.length(); i++) {
            if (versions.getJSONObject(i).has("releaseDate") && versions.getJSONObject(i).has("id")) {
                String id = versions.getJSONObject(i).get("id").toString();
                //search version by id from versionList
                Version version = searchVersion(id);
                if (version == null) continue;
                affectedVersions.add(version);
            }
        }
        return affectedVersions;
    }

    public @Nullable Version getVersionAfter(LocalDate date){
        for (Version version : this.versionList) {
            if(!version.getDate().isBefore(date)){
                return version;
            }
        }
        return null;
    }



    private void getVersions(String projectName) throws IOException, JSONException {
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectName;
        JSONObject json = JsonUtils.readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");
        this.versionList = createArray(versions);

        this.versionList.sort(Comparator.comparing(Version::getDate));

        int i = 0;
        for(Version version: this.versionList){
            version.setIndex(i);
            i++;
        }

    }
    private @Nullable Version searchVersion(String id){
        for (Version version : this.versionList) {
            if (Objects.equals(version.getId(), id)) {
                return version;
            }
        }
        //if
        return null;
    }

    private List<Version> createArray(JSONArray versions) throws JSONException {
        List<Version> versionList = new ArrayList<>();
        for (int i = 0; i < versions.length(); i++ ) {
            String name = "";
            String id = "";
            if(versions.getJSONObject(i).has("releaseDate")) {
                if (versions.getJSONObject(i).has("name"))
                    name = versions.getJSONObject(i).get("name").toString();
                if (versions.getJSONObject(i).has("id"))
                    id = versions.getJSONObject(i).get("id").toString();

                LocalDate date = LocalDate.parse(versions.getJSONObject(i).get("releaseDate").toString());
                Version version = new Version(id, name, date);
                versionList.add(version);
            }
        }
        return  versionList;
    }

    public List<Version> getVersionList() {
        return versionList;
    }
}
