# BIOP Operetta Tools

Scripts and tools used for the PerkinElmer Operetta System at the EPFL BioImaging & Optics Platform. 
For a more thorough tutorial on our recommendations for using our PerkinElmer Operetta and data export protocols, you can check out our [BIOP Wiki page](https://wiki-biop.epfl.ch/en/Microscopy/WF15-PerkinElmerOperettaCLS)

# Export flatfield images

The Harmony software from the Operetta system is capable of providing "flatfield corrected" images of acquisitions on the fly. Exporting these flatfield images is however not trivial. 

Upon discussion with PerkinElmer, we have been allowed to share the following script that allows the export of images with the flatfield correction applied to the images. 


> **Warning**
> This script requires a valid Harmony license to work, as it calls PerkinElmer's software directly. 

## Flatfield export script and protocol

### Before running the script 

Make sure that the `Export_Flatfielded_Images_biop.bat` contains the location of the `Acapella.exe` file. For us it is currently in `%programfiles%\PerkinElmer\Harmony 4.9\ACServ\` but this will change with newer versions of Harmony. 

Make sure you download both `Export_Flatfielded_Images_biop.bat` and `Export_Flatfielded_Images_biop.script` to a local folder before starting the export protocol. 

### Export prototol
1. Export the measurements from Harmony with “Referenced Images”, i.e. you will just get an “index.ref.xml” file.

2. Run the batch (.bat) file located in this repo's `Operetta flatfield export` folder.

3. The script will then ask for an index.xml file. Select the file that was created during the Harmony export of the previous step.

4. The script will create a new folder and write all flatfield corrected images into that folder.

5. It will also generate a new appropriate `index.xml` file.

The resulting flatfield iamgesd can now be processed either using the [BIOP Operetta Importer](https://github.com/BIOP/ijp-operetta-importer) or imported onto OMERO with all metadata intact. 

> **Note**
> The orignal script provided by PerkinElmer is provided in the `Original Script` folder. It uses a different naming convention for the resulting XML file, (appends `_flex` to the name), which is incompatible with importing the data to OMERO.

# Visualizing Operetta data

To load Operetta data, whether it is flatfieled or not, the following tools are available

| Tool | Link | Pros | Cons |
| -----|------|------|------|
| BIOP Operetta Importer | https://github.com/BIOP/ijp-operetta-importer | Can load, fuse and export tiled versions as tiffs | Requires yet another export. |
| BDV Playground | https://imagej.net/plugins/bdv/playground | Has a dedicated `Open [Operetta Dataset]` command for BigDataViewer | Visualization and export are in beta.  |
| QuPath | https://qupath.github.io/ | Native support to open the `index.xml` file| Only displays individual fields (no stitching). Can become unresponsive with too many images in the QuPath project|
|Old Operetta Import Script | [BIOP_Operetta_Import.groovy](main/deprecated/BIOP_Operetta_Import.groovy) | Just one script | No longer maintained. use [BIOP Operetta Importer](https://github.com/BIOP/ijp-operetta-importer) |

The `BIOP_Operetta_Import.groovy` script in the `deprecated` folder is the precursor to the [BIOP Operetta Importer](https://github.com/BIOP/ijp-operetta-importer) plugin which is available though the PTBIOP Update site