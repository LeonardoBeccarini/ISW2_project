package org.example.controller.retriever;

import org.example.utils.JsonUtils;
import org.example.model.Version;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private @Nullable Version searchVersion(String id){
        for (Version version : this.versionList) {
            if (Objects.equals(version.getId(), id)) {
                return version;
            }
        }
        //if
        return null;
    }

    public void deleteVersionWithoutCommits(){
        versionList.removeIf(Version::isCommitListEmpty);
        this.versionList.sort(Comparator.comparing(Version::getDate));
        setIndex();
    }

    private void getVersions(String projectName) throws IOException, JSONException {
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectName;
        JSONObject json = JsonUtils.readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        this.versionList = createArray(versions);

        versionList.sort(Comparator.comparing(Version::getDate));
        removeOutOfOrderPatchVersions(versionList);
        setIndex();
    }
    private void setIndex(){
        int i = 0;
        for(Version version: this.versionList){
            version.setIndex(i);
            i++;
        }
    }
    private void removeOutOfOrderPatchVersions(List<Version> versions) {
        Pattern semver = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
        Set<Version> toRemove = new HashSet<>();

        for (Version v : versions) {
            Matcher m = semver.matcher(v.getName());
            if (!m.matches()) continue;

            int major = Integer.parseInt(m.group(1));
            int minor = Integer.parseInt(m.group(2));
            int patch = Integer.parseInt(m.group(3));
            if (patch == 0) continue;               // skip “.0” entries

            String nextMinorZero = String.format("%d.%d.0", major, minor + 1);
            versions.stream()
                    .filter(v2 -> v2.getName().equals(nextMinorZero))
                    .findFirst()
                    .ifPresent(v2 -> {
                        // if the patch’s date is after the minor-zero’s date, drop the patch
                        if (v.getDate().isAfter(v2.getDate())) {
                            toRemove.add(v);
                        }
                    });
        }

        versions.removeAll(toRemove);
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