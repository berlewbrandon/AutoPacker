package no.autopacker.filedeliveryapi.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import no.autopacker.filedeliveryapi.database.ModuleRepository;
import no.autopacker.filedeliveryapi.database.MongoDb;
import no.autopacker.filedeliveryapi.database.ProjectRepository;
import no.autopacker.filedeliveryapi.domain.ModuleMeta;
import no.autopacker.filedeliveryapi.domain.ProjectMeta;
import no.autopacker.filedeliveryapi.service.DockerService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.adapters.RefreshableKeycloakSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ModuleController {

    private final ProjectRepository projectRepo;
    private final ModuleRepository moduleRepo;
    private final DockerService dockerService;
    private final MongoDb mongo;

    @Autowired
    public ModuleController(ProjectRepository projectRepo, ModuleRepository moduleRepo,
        DockerService dockerService, MongoDb mongo) {
        this.projectRepo = projectRepo;
        this.moduleRepo = moduleRepo;
        this.dockerService = dockerService;
        this.mongo = mongo;
    }

    /**
     * Upload a module to project as a compressed folder if you're the project owner or admin.
     *
     * @param username    project owner
     * @param projectName project name
     * @param moduleName  name of module
     * @param configType  type of configuration to use for dockerfile
     * @param moduleFiles list of files to upload to the module
     * @return status ok if module was added, status unauthorized if user is not logged in, status
     * not found if project was not found or status internal error if module couldn't be deleted
     */
    @RequestMapping(value = "/projects/{username}/{project}/{module}/add", method = RequestMethod.POST,
        consumes = "multipart/form-data")
    public ResponseEntity uploadModuleToProject(@PathVariable("username") String username,
        @PathVariable("project") String projectName,
        @PathVariable("module") String moduleName,
        @RequestParam("config-type") String configType,
        @RequestParam("config-params") String configParamsJson,
        @RequestParam("module-file") List<MultipartFile> moduleFiles) {
        ResponseEntity response;
        ProjectMeta project = projectRepo.findByOwnerAndName(username, projectName);
        KeycloakPrincipal<RefreshableKeycloakSecurityContext> authenticatedUser = (KeycloakPrincipal<RefreshableKeycloakSecurityContext>) SecurityContextHolder
            .getContext().getAuthentication().getPrincipal();

        if (authenticatedUser != null) {
            if (authenticatedUser.getKeycloakSecurityContext().getToken().getPreferredUsername()
                .equalsIgnoreCase(username) || authenticatedUser.getKeycloakSecurityContext()
                .getToken().getResourceAccess("file-delivery-api").isUserInRole("ADMIN")) {
                if (project != null) {
                    if (!moduleRepo.moduleExists(project.getId(), moduleName)) {
                        // Checks if the project has a valid name
                        Pattern pattern = Pattern.compile("[\\w-]*");
                        Matcher matcher = pattern.matcher(moduleName);

                        if (matcher.matches()) {
                            String modulePath = project.getLocation()
                                .concat(moduleName + File.separator);

                            // Take out program language and version from config type
                            String[] configTypeSplit = configType.split("-");
                            String language = configTypeSplit[0];
                            String version;

                            if (configTypeSplit.length > 1) {
                                version = configTypeSplit[1];
                            } else {
                                version = "N/A";
                            }

                            int port = 0;
                            try {
                                JSONObject json = new JSONObject(configParamsJson);

                                if (json.has("port")) {
                                    port = json.getInt("port");
                                } else if (json.has("PORT")) {
                                    port = json.getInt("PORT");
                                }
                            } catch (JSONException je) {
                                je.printStackTrace();
                                response = ResponseEntity.badRequest()
                                    .body("Something wrong happened parsing port(s)");
                            }

                            ModuleMeta module = new ModuleMeta(moduleName, port, language, version,
                                configType, modulePath, project.getId());
                            moduleRepo.save(module);

                            // Get module id after module has been inserted and put it in mongodb
                            long moduleId = moduleRepo
                                .findModuleByUsernameAndProjectNameAndModuleName(username,
                                    projectName, moduleName)
                                .getId();
                            mongo.save(moduleId, configParamsJson);

                            try {
                                // Create the module directory
                                new File(modulePath).mkdirs();

                                // Check if only one file is uploaded, then check if its a zip
                                if (moduleFiles.size() == 1) {
                                    MultipartFile singleUploadedFile = moduleFiles.get(0);
                                    String fileMimeType = singleUploadedFile.getContentType();

                                    // Check if the file is a zip
                                    if (fileMimeType.equals("application/zip") || fileMimeType
                                        .equals("application/x-zip-compressed")) {

                                        // Convert the uploaded multipart file into a zip and place it inside module folder
                                        File tempFile = new File(modulePath,
                                            System.currentTimeMillis() + ".zip");
                                        FileOutputStream tempFileFos = new FileOutputStream(
                                            tempFile);
                                        IOUtils
                                            .copy(singleUploadedFile.getInputStream(), tempFileFos);
                                        tempFileFos.close();
                                        singleUploadedFile.getInputStream().close();

                                        // Fetch all the files inside the zip
                                        ZipFile zip = new ZipFile(tempFile);
                                        ZipInputStream zipInputStream = new ZipInputStream(
                                            singleUploadedFile.getInputStream());
                                        ZipEntry zipEntry = zipInputStream.getNextEntry();

                                        // Iterate through the files inside zip
                                        while (zipEntry != null) {
                                            File zipFile = new File(
                                                modulePath.concat(zipEntry.getName()));
                                            // Check if the current file is a dir, if so, create it
                                            if (zipEntry.isDirectory()) {
                                                zipFile.mkdirs();
                                            } else {
                                                if (!zipFile.getParentFile().exists()) {
                                                    zipFile.getParentFile().mkdirs();
                                                }
                                                zipFile.createNewFile();
                                                FileOutputStream fos = new FileOutputStream(
                                                    zipFile);        // output stream from the empty file
                                                IOUtils.copy(zip.getInputStream(zipEntry),
                                                    fos);            // input stream from the file inside the zip
                                                fos.close();
                                                zip.getInputStream(zipEntry).close();
                                            }

                                            // Goto next zip entry in zip
                                            zipEntry = zipInputStream.getNextEntry();
                                        }

                                        // Remove the file locks
                                        zipInputStream.close();
                                        singleUploadedFile.getInputStream().close();
                                        zip.close();
                                        tempFile.delete();

                                    } else {
                                        uploadFilesFromMultipart(moduleFiles, modulePath);
                                    }
                                } else {
                                    uploadFilesFromMultipart(moduleFiles, modulePath);
                                }

                                // Build the module (may take some time)
                                dockerService.buildDockerImage(module, project.getOwner());
                                response = ResponseEntity.ok("Module has been added to project!");
                            } catch (Exception e) {
                                e.printStackTrace();
                                StringWriter sw = new StringWriter();
                                PrintWriter pw = new PrintWriter(sw);
                                e.printStackTrace(pw);
                                String sStackTrace = sw.toString(); // stack trace as a string
                                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body(sStackTrace);
                            }
                        } else {
                            response = ResponseEntity.badRequest()
                                .body(
                                    "The project name can only contain alphanumeric values combined with dash and underscore");
                        }
                    } else {
                        response = ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("Module already exists");
                    }
                } else {
                    response = ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Project does not exist");
                }
            } else {
                response = ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You have no permission to perform this action");
            }
        } else {
            response = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("You must be logged in to perform this action");
        }

        // If something went wrong after the module was added to the database, remove it from db
        if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
            // Find module
            ModuleMeta module = moduleRepo
                .findModuleByUsernameAndProjectNameAndModuleName(username, projectName, moduleName);

            if (module != null) {
                // Remove from database
                mongo.deleteByModuleId(module.getId());
                moduleRepo.delete(module.getId());

                // Delete the module files and folders
                File moduleDir = new File(module.getLocation());
                if (moduleDir.exists() && moduleDir.isDirectory()) {
                    try {
                        FileUtils.cleanDirectory(moduleDir);
                        FileUtils.deleteDirectory(moduleDir);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return response;
    }

    /**
     * Upload a pre-created game server to a specific project as a module
     *
     * @param username the username of the user uploading the module
     * @param projectName the name of the project to upload the module to
     * @param moduleName the name of the new module
     * @param configType the game server configuration tyoe
     * @param configParamsJson different configuration parameters like port
     * @return status ok if module was added, status unauthorized if user is not logged in, status
     *         not found if project was not found or status bad request if something failed
     */
    @PostMapping(value = "/projects/{username}/{project}/{module}/game-module/add")
    public ResponseEntity<String> uploadGameServerToProject(@PathVariable("username") String username,
                                                            @PathVariable("project") String projectName,
                                                            @PathVariable("module") String moduleName,
                                                            @RequestParam("config-type") String configType,
                                                            @RequestParam("server-version") String serverVersion,
                                                            @RequestParam("config-params") String configParamsJson) {
        KeycloakPrincipal<RefreshableKeycloakSecurityContext> authenticatedUser = (KeycloakPrincipal<RefreshableKeycloakSecurityContext>) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        if (authenticatedUser.getKeycloakSecurityContext().getToken().getPreferredUsername().equalsIgnoreCase(username)) {

            ProjectMeta project = this.projectRepo.findByOwnerAndName(username, projectName);
            if (project != null) {
                // Check if module by given name already exists
                if (!moduleRepo.moduleExists(project.getId(), moduleName)) {
                    // Checks if the project has a valid name
                    Pattern pattern = Pattern.compile("[\\w-]*");
                    Matcher matcher = pattern.matcher(moduleName);

                    if (matcher.matches()) {
                        String modulePath = project.getLocation().concat(moduleName + File.separator);

                        // Get config parameters (port)
                        int port = 0;
                        try {
                            JSONObject json = new JSONObject(configParamsJson);

                            if (json.has("port")) {
                                port = json.getInt("port");
                            } else if (json.has("PORT")) {
                                port = json.getInt("PORT");
                            }
                        } catch (JSONException je) {
                            je.printStackTrace();
                            return ResponseEntity.badRequest()
                                    .body("Something wrong happened parsing port(s)");
                        }

                        // Create the module meta entity
                        ModuleMeta module = new ModuleMeta(moduleName, port, configType, serverVersion,
                                configType, modulePath, project.getId());
                        moduleRepo.save(module);

                        // Get module id after module has been inserted and put it in mongodb
                        long moduleId = moduleRepo
                                .findModuleByUsernameAndProjectNameAndModuleName(username,
                                        projectName, moduleName)
                                .getId();
                        mongo.save(moduleId, configParamsJson);

                        return ResponseEntity.ok("Module successfully added to the project");
                    } else {
                        return ResponseEntity.badRequest().body("The project name can only contain alphanumeric values combined with dash and underscore");
                    }
                } else {
                    return ResponseEntity.badRequest().body("Module with that name already exists");
                }
            } else {
                return ResponseEntity.badRequest().build();
            }
        } else {
            return new ResponseEntity<>("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
    }


    /**
     * Delete a module inside a project if you're the owner or admin.
     *
     * @param username    project owner
     * @param projectName project name
     * @param moduleName  name of module
     * @return status ok if project was deleted, status not found if project or module is not found,
     * internal error if module couldn't be deleted or ,status unauthorized if user is not logged
     * in
     */
    @RequestMapping(value = "/projects/{username}/{project}/{module}", method = RequestMethod.DELETE)
    public ResponseEntity deleteProjectModule(@PathVariable("username") String username,
        @PathVariable("project") String projectName,
        @PathVariable("module") String moduleName) {
        ProjectMeta pm = projectRepo.findByOwnerAndName(username, projectName);
        KeycloakPrincipal<RefreshableKeycloakSecurityContext> authenticatedUser = (KeycloakPrincipal<RefreshableKeycloakSecurityContext>) SecurityContextHolder
            .getContext().getAuthentication().getPrincipal();
        ResponseEntity response;

        if (authenticatedUser != null) {
            if (authenticatedUser.getKeycloakSecurityContext().getToken().getPreferredUsername()
                .equalsIgnoreCase(username) || authenticatedUser.getKeycloakSecurityContext()
                .getToken().getResourceAccess("file-delivery-api").isUserInRole("ADMIN")) {
                if (pm != null) {
                    ModuleMeta module = moduleRepo
                        .findModuleByProjectIdAndName(pm.getId(), moduleName);
                    if (module != null) {
                        File moduleDir = new File(module.getLocation());
                        if (moduleDir.exists()) {
                            try {
                                FileUtils.cleanDirectory(moduleDir);
                                FileUtils.deleteDirectory(moduleDir);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            // If the module files are deleted, remove the reference to database
                            if (!moduleDir.exists() || moduleDir.delete()) {
                                moduleRepo.delete(module.getId());
                                mongo.deleteByModuleId(module.getId());
                                response = ResponseEntity.ok("Module '" + moduleName + "' deleted");
                            } else {
                                response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body("Couldn't delete module");
                            }
                        } else {
                            response = ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body("Module does not exist");
                        }
                    } else {
                        response = ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body("Module does not exist");
                    }
                } else {
                    response = ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Project does not exist");
                }
            } else {
                response = ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You do not have permissions to delete this module");
            }
        } else {
            response = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("You must be logged in to perform this action");
        }

        return response;
    }

    private void uploadFilesFromMultipart(List<MultipartFile> moduleFiles, String modulePath)
        throws Exception {
        // Save each file under the module folder
        for (MultipartFile uploadedFile : moduleFiles) {
            // Concat the folder of the module with the file name to get absolute path of file
            String moduleFileLocation = modulePath.concat(uploadedFile.getOriginalFilename());
            // This prevents having different types of slashes in path (i.e. /usr/test\123.txt)
            String normalizedFileLocation = new File(moduleFileLocation).toPath().toString();

            // If the file is under a directory:
            if (uploadedFile.getOriginalFilename().contains("\\") || uploadedFile
                .getOriginalFilename().contains("/")) {
                // Find the directory, including sub dirs
                String finalFilePath = normalizedFileLocation
                    .substring(0, normalizedFileLocation.lastIndexOf(File.separator));

                // Create all the directories for the file uploaded
                File moduleFilePath = new File(finalFilePath);
                if (!moduleFilePath.exists()) {
                    moduleFilePath.mkdirs();
                }
            }

            // Write file content into disk from multipart file object
            File moduleFile = new File(normalizedFileLocation);
            moduleFile.createNewFile();
            FileOutputStream fos = new FileOutputStream(moduleFile);
            IOUtils.copy(uploadedFile.getInputStream(), fos);

            fos.close();
            uploadedFile.getInputStream().close();
        }
    }
}
