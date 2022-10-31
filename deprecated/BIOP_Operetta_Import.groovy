#@File(label="Select your directory with your exported images", style="directory") theDir
#@File(label="Select directory to save images", style="directory", value="default") saveDir
#@Integer(label="Resize Factor", value=1) resize
#@String(label="Export file(s) as", choices={"Fused Fields","Individual Fields" } ) export_files
#@Boolean(label="Only Process Selected Wells", value=false, persist=false) is_select_wells
#@String(label="X Y W H of box to extract", value="") str_xywh 
#@Boolean(label="Project Z Slices", value=false) is_do_z_project
#@String(label="Z Projection Method", choices={"Max Intensity", "Sum Slices", "Average Intensity", "Standard Deviation", "Median"}) z_project_method

#@Boolean(label="Export Sub Fields", value=false) is_do_fields
#@String(label="Fields(s) to Export", value="") fields_for_export_str

#@Boolean(label="Export Sub Channels", value=false) is_do_channels
#@String(label="Channel(s) to Export", value="") channels_for_export_str

#@Boolean(label="Export Sub Slices", value=false) is_do_slices
#@String(label="Slice(s) to Export", value="") slices_for_export_str

#@Boolean(label="Export Sub Timepoint", value=false) is_do_timepoints
#@String(label="Timepoint(s) to Export", value="") timepoints_for_export_str

#@String (visibility=MESSAGE, value="In case of 32-bit images, intensity rescaling parameters", persist=false, required=false) msg

#@Double (label="32-bit Rescaling Minimum Intensity", value=0) min_32
#@Double (label="32-bit Rescaling Maximum Intensity", value=10000) max_32

// ---------------- DESCRIPTION ----------------- //

/*
 * PERKIN ELMER OPERETTA STITCHER
 * v4.1,  December 2018
 * This tool allows for the reshaping (requires resaving) 
 * of tiffs exported with the Operetta Symphony software 
 * so as to be be viewed and processed with Fiji (or other softwares)
 * as time lapse (stitched or not)
 * 
 * This tool can export individual fields or tile all fields in each well
 * to produce a large image stack.
 * The output is either 
 * - One hyperstack per field per well (CZT)
 * - One large (tiled) hyperstack per well (CZT)
 * 
 * For faster export and preview, we offer the possibility to downsample the images before exporting them,
 * significantly reducing processing time. 
 * 
 * In order to maximize export speed (Especially due to PerkinElmer using zip-compressed TIFFS,
 * we benefit from the Gpars for parallel processing library, so there are a
 * few dependencies not bundled with ImageJ/Fiji
 * See https://c4science.ch/w/bioimaging_and_optics_platform_biop/image-processing/imagej_tools/perkinelmer-stitching/
 * For dependencies and instructions
 * 
 * Authors: Olivier Burri, Romain Guiet
 * BioImaging and Optics Platform (BIOP)
 * Ecole Polytechnique Fédérale de Lausanne
 * 
 * Change Log:
 * September 2017 : First version that can tile all fields in wells, parallelized
 * 
 *   October 2017 : Added  possibility of downsampling 
 *   				Added the possibility of defining a ROI
 *   				Added a GUI to select which wells to export
 *   				
 *  December 2017 : Added possibility to save individual fields, which rewrote most of the tool
 *  				Changed some naming conventions, discussing with Romain
 *  				
 *   January 2018 : Fixed issue where simple stacks could not be saved due to HyperStackConverter error
 *   				Added error correction for filename when PE export uses erroneous convention
 *   					XML File    : r01c01f01p01-ch1sk1fk1fl1.tiff
 *   					Actual file : r01c01f001p01-ch1sk1fk1fl1.tiff
 *   				Fixed wrong memory calculation. Bug was introduced in December version
 *   					
 *  February 2018 : Changes the GUI to a dropDown menu of "Export file(s) as"
 *  
 *     March 2018 : Fixes an issue where Z Projections would also project timepoints
 *     
 *     April 2018 : Adds the possibility to define an output folder or enter 'default'
 *     				to let the software put it in an 'output' folder under tha main image folder
 *     
 *     	July 2018 : Fixes an error where if it could not find a matching image, it would make a copy of the next channel
 *     				Fixes an error where images could be 32-bit (digital phase), so it converts them (no scaling) back to 16
 *     				
 * September 2018 : Fixes an issue where groovy interprets n_wells_parallel as Long instead of int, on mac only...
 *   				Adds support for export ranges for fields, slices, channels, timepoints
 *   				
 * January 2022  : Moves setCalibration so it works with Projection  				
 *   				
 * Copyright 2018 Olivier Burri, Romain Guiet 
 * 
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *    
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

// ------------------ IMPORTS ------------------ //

import System.*

import groovy.util.XmlSlurper

import ij.*

import ij.gui.*
import ij.plugin.*
import ij.process.*
import ij.measure.Calibration

// Play with parallel stuff
import groovyx.gpars.GParsPool
import groovyx.gpars.GParsExecutorsPool

// Number converter
import java.text.DecimalFormat

// GUI goodness
import groovy.swing.SwingBuilder 
import javax.swing.* 
import java.awt.* 

// This is to fix a bug in the way Perkin Elmer export their files...
import groovy.io.FileType
import groovy.transform.ToString

// ------------------  SCRIPT  ------------------ //

// turn the user choice as a boolean
def isTile = false 
if ( export_files =~"Fused Fields" ) isTile = true 

// Create an instance of the PerkinElmer Opener
def pe = new PerkinElmerOpener()

pe.setSaveDirectory(saveDir)
// Test mode only processes the first two wells
//pe.setTestMode(true) 

// Selects whether we should assemble all fields or not
pe.setIsTile(isTile)

pe.setIsProjectZ(is_do_z_project)
pe.setzProjectionMethod(z_project_method)

pe.setIsSubFields(is_do_fields)
pe.setFieldsToProcess(fields_for_export_str)

pe.setIsSubChannels(is_do_channels)
pe.setChannelsToProcess(channels_for_export_str)


pe.setIsSubSlices(is_do_slices)
pe.setSlicesToProcess(slices_for_export_str)

pe.setIsSubTimepoints(is_do_timepoints)
pe.setTimepointsToProcess(timepoints_for_export_str)

// Set the ROI as needed
pe.setROIFromString(str_xywh)

pe.parseXML(theDir)

pe.setMinMaxDispForRescale(min_32, max_32)

// If we want to use the GUI, call it here
if(is_select_wells) {
	
	// Because GUIs in Java are not attached to the main thread
	// We cannot just call it, wait for user and then process. 
	// the Run button in the GUI is the one that must run the process...
	pe.selectWellsGUI(resize)
	
} else {
	
	// Process all images in parallel
	def processor = new Timer("Processing")
	processor.tic()
	pe.process(resize) // Where the magic happens
	processor.toc()
}

// -----------------  CLASSES  ----------------- //


/* 
 *  The big boy that does everything
 *  Which involves 2 steps.
 *  1. Parsing the xml file
 *  2. Exporting the selected wells
 */
class PerkinElmerOpener {
	File saveDirectory = null
	boolean isTest = false
	boolean isTile = true
	Roi     roi = null

	boolean isSubFields = false
	ArrayList fieldsToProcess = []
	String fieldsString = ""

	
	boolean isSubChannels = false
	ArrayList channelsToProcess =  []
	String channelsString = ""
	
	boolean isSubSlices = false
	ArrayList slicesToProcess = []
	String slicesString = ""
	
	boolean isSubTimepoints = false
	ArrayList timepointsToProcess = []
	String timepointsString = ""
	
	boolean isProjectZ = false
	String zProjectionMethod = "Max Intensity"
	
	int nPlanesParallel
	
	def allFiles
	def allIds
	
	ExperimentMetadata metadata
	
	HashSet selectedWells

	double min_32 = 0
	double max_32 = 10000
	// to help format numbers for console output
	DecimalFormat df = new DecimalFormat("##.##")


	void setSaveDirectory(File saveDir) {
		if(saveDir.getName() != "default") {
			this.saveDirectory = new File(saveDir.getAbsolutePath())
		}
	}
	
	void setROIFromString(String roi_str) {
		if (roi_str.size() > 7) {
			def coordinates = roi_str.tokenize(' ').collect{ it.toInteger() }
			setRoi(new Roi(coordinates[0], coordinates[1], coordinates[2], coordinates[3]))
		}
	}


	void setFieldsToProcess(String fields_str) {
		if( isSubFields ) this.fieldsToProcess = parseString( fields_str )
		this.fieldsString = fields_str
	}

	void setChannelsToProcess(String channels_str) {
		if( isSubChannels ) this.channelsToProcess = parseString( channels_str )
		this.channelsString = channels_str
	}

	void setTimepointsToProcess(String timepoints_str) {
		if( isSubTimepoints ) this.timepointsToProcess = parseString( timepoints_str )
		this.timepointsString = timepoints_str
	}

	void setSlicesToProcess(String slices_str) {
		if( isSubSlices ) this.slicesToProcess = parseString( slices_str )
		this.slicesString = slices_str
	}

	ArrayList parseString(String text) {
		def individual = text.split(',')
		def numList = []
		individual.each{
			// split again to get sequences
			def seq = it.split(':')
			if ( seq.size() == 2 ) {
				def sublist = ( ( seq[0] as int ) .. ( seq[1] as int ) )
				numList.addAll(sublist )
			} else {
				numList.add(it as int)
			}
		}
		return numList
	}

	// Setting the min and max display range for rescaling 32-bit images
	void setMinMaxDispForRescale(double min, double max) {
		this.min_32 = min
		this.max_32 = max
	}

	double getMinDispForRescale() {
		return this.min_32
	}

	double getMaxDispForRescale() {
		return this.max_32
	}
	
	// Method to parse the exported XML file and get all the information regarding the experiment
	void parseXML(File dir) {
		IJ.log("Parsing XML File... please wait")
		// Set a timer
		def parser = new Timer("XML Parser")
		parser.tic()
		
		// ExperimentMetadata contains all the boring stuff to make sense of the PE file and help the extraction of the data
		metadata = new ExperimentMetadata()
		
		// Set the location of the data to export
		metadata.setParentDirectory(dir)

		// If save directory was explicitely set
		if(this.saveDirectory != null ) metadata.setSaveDirectory(this.saveDirectory)

		
		// Parsing XML file
		def xml = new XmlSlurper().parse(metadata.getXMLFile())
		
		// Get the experiment name from Plate ID
		metadata.setExperimentName(xml.Plates.Plate.PlateID.toString())
		
		// Get the image size and pixel size
		metadata.setImageSize( xml.Images.Image[0].ImageSizeX.toInteger(), xml.Images.Image[0].ImageSizeY.toInteger() )
		
		metadata.setPixelSize( xml.Images.Image[0].ImageResolutionX.toDouble() )
		
		// From the xml file, create list of informations for each Image
		def ims = new 	ArrayList<Image>()
		
		// Parse all the image data
		xml.Images.Image.each{
			def im = new Image()
		
			im.rowcol.add(it.Row.toInteger())
			im.rowcol.add(it.Col.toInteger())
			
			im.field   	= it.FieldID.toInteger()
			im.channel 	= it.ChannelID.toInteger()
			im.slice   	= it.PlaneID.toInteger()
			im.timepoint= it.TimepointID.toInteger()
			im.posx   	= it.PositionX.toFloat()
			im.posy   	= it.PositionY.toFloat()
			im.posz     = it.PositionZ.toFloat()
			im.toffset  = it.MeasurementTimeOffset.toFloat()
			im.image    = it.URL.text()

			def addField   = true
			def addChannel   = true
			def addSlice     = true
			def addTimepoint = true
			
			// Choose whether to add or not depending on the list of channels, slices, and frames
			if( isSubFields   )   addField        = ( im.field  in fieldsToProcess )			
			if( isSubChannels )   addChannel      = ( im.channel  in channelsToProcess )
			if( isSubSlices   )   addSlice        = ( im.slice    in slicesToProcess )
			if( isSubTimepoints ) addTimepoint    = ( im.timepoint in timepointsToProcess )
			//println(im.toString())
			//println("field: "+addField)
			//println("ch: "+addChannel)
			//println("z: "+addSlice)
			//println("t: "+addTimepoint)
			
			if( addField && addChannel && addSlice && addTimepoint ) ims.add(im) 
		}
		// Append image data to the metadata
		metadata.setImageData(ims)

		
		// BUG: Need the actual files in the folder to fix the naming conventions
		metadata.setFolderFiles()

		allFiles = metadata.getFolderFiles()

		// Create an ID for all files, that way we can reuse them directly
		allIds = allFiles.collect{ 
			def group = (it.name =~ /r(\d*)c(\d*)f(\d*)p(\d*)-ch(\d*).*/) 
			if (!group.hasGroup() ) return [r:0 as int, c:0 as int, f:0 as int, p:0 as int, ch:0 as int]
			return [r:group[0][1] as int, c:group[0][2] as int , f:group[0][3] as int, p:group[0][4] as int, ch:group[0][5] as int]
			}
		parser.toc()
		
	}
	
	void process(int resize) {
		// Need to process the data in the following way
		// A certain number of fields in parallel, each with a certain number of parallel openings and closings
		// If processing a tiled dataset, copy the full field to the tile stack
		// If no tiling, save each field and store their coordinates in a positions.txt file
		
		// Check how many open images we can work on
		def maxRam = IJ.maxMemory() / 1e9 * 0.95
		
		
		def fieldSize = metadata.getFieldSize(resize)
		def fieldStackRamSize = metadata.getFieldStackSize(resize)
		
		def wellSize = metadata.getWellSize(resize)
		def wellStackRamSize = metadata.getWellStackSize(resize)
		
		def fieldsPerWell  = metadata.getFieldsPerWell()
		
		def theCalibration = metadata.computeCalibration(resize)
		
		def cztDims = metadata.getStackCZTDimensions()

		def nPlanes = metadata.getPlanesPerField()
		
		// Compute how many fields and wells we can have in parallel
		int nWellsParallel
		
		if( isTile ) {
			// In the case of a tile, for each well we want to process in parallel we need the memory for all the fields and for the full stacks
			def nParallelTiles = Math.round( maxRam / ( fieldStackRamSize * fieldsPerWell + wellStackRamSize ) )
			nPlanesParallel = fieldsPerWell
			nWellsParallel  = nParallelTiles > 2 ? nParallelTiles : 1
			
		} else {
			// For fields only, we just need to compute hopw many fields in parallel we can work on
			def nFieldsRaw = Math.round( maxRam / (fieldStackRamSize) )
			nPlanesParallel = nFieldsRaw > 10 ?  10 : nFieldsRaw
			
			def nWellsParallelRaw  = Math.round(maxRam / ( fieldStackRamSize * 3 ) )
			nWellsParallel = nWellsParallelRaw > 2 ? nWellsParallelRaw : 1
		}
		
		// Output some data to the user via the log
		
		IJ.log("One Field of CZT image stack is "+ df.format(fieldStackRamSize) + " GB.")
		if (isTile) {
			IJ.log("One Tiled CZT image stack is "+ df.format(wellStackRamSize) + " GB.")
		}
		IJ.log("There are "+fieldsPerWell+" fields in each well")
		IJ.log("And you have "+ (df.format(maxRam) ) + " GB of RAM")
		IJ.log("--->We will try to work on "+nWellsParallel+" wells in parallel and "+nPlanesParallel+" extra threads to process your data")
		
		this.selectedWells = metadata.getSelectedWells()
		
		if(this.isTest) {
			this.selectedWells = 	this.selectedWells.take(2)		
		}

		// ExecutorsPool is less optimized than GParsPool but this  way we can nest calls :)
		GParsExecutorsPool.withPool(nWellsParallel as int) {
		selectedWells.eachWithIndexParallel{ well, i ->
			sleep(new Random().nextInt() % 5000)
			IJ.log("\nProcessing Well "+well)
			
			
			def field_positions = []
			def field_stack_names = []
			
			def well_stack
			def well_stack_name = confirmName(metadata.getWellName(well))

			
			def well_image
			if (isTile ) {
				if(roi != null) {
					def bounds = roi.getBounds()
					
					checkTime( "Creating Well Stack", { well_stack = ImageStack.create((int)bounds.width, (int)bounds.height, nPlanes, 16 ) } )
				} else {
					checkTime( "Creating Well Stack", { well_stack = ImageStack.create((int) wellSize['x'], (int) wellSize['y'], nPlanes, 16 ) } )
				}
				// Prepare final ImagePlus here so we can access the getStackIndex function
				well_image = new ImagePlus(well_stack_name, well_stack)
				well_image.setDimensions(cztDims.c, cztDims.z, cztDims.t)
			}
			
			(1..fieldsPerWell).each{ field ->
				def field_stack
				
				def field_stack_name = confirmName(metadata.getFieldName(well, field))
				
				if(roi != null && !isTile) {
					def bounds = roi.getBounds()
					checkTime( "Creating Field Stack", { field_stack = ImageStack.create((int)bounds.width, (int)bounds.height, nPlanes, 16 ) } )
				} else {
					checkTime( "Creating Field Stack", { field_stack = ImageStack.create((int) fieldSize['x'], (int) fieldSize['y'], nPlanes, 16 ) } )
				}
				
				// Prepare final ImagePlus here so we can access the getStackIndex function
				def field_image = new ImagePlus(field_stack_name, field_stack)
				field_image.setDimensions(cztDims.c, cztDims.z, cztDims.t)
				// Image name
				if(resize != 1) {
				field_stack_name+="-Resized "+resize
				}
				// Name of each field image for saving as tilepositions if needed
				field_stack_names[field-1] = field_stack_name
				
				//Save the position for this field with the name, so as to write the tile configuration file
				field_positions[field-1] = metadata.getPixelCoordinates(field, resize)
				
				GParsExecutorsPool.withPool(nPlanesParallel as int) {
				metadata.getAllCZT().eachParallel{ czt ->
					//print("Processing CZT: "+czt.toString())
					// Get the image matching this CZT
					def current_image = metadata.findImage(well, czt, field)
					// The operetta system does not save images in case of failed autofocus for example
					if(current_image != null) {
					
						// Open the image
						def current_imp
						//print("\nOpening Image "+metadata.getParentDirectory() +"/"+ current_image.image.toString())
						checkTime( "Opening Single Slice", { current_imp = tryOpening(metadata.getParentDirectory() +"/"+ current_image.image.toString()) } )
						
						// Had issues with some being null once in a while...? concurrency issue of IJ.openImage()?
						if(current_imp != null) {
							// resize the image as requested and add it to the large slice
							def current_ip = current_imp.getProcessor().resize((int) (fieldSize['x']))
							
							//  if a ROI was defined, crop it before adding it
							if (roi != null && !isTile) {
								current_ip.setRoi(roi)
								current_ip = current_ip.crop()
							}
							
							// Now add this image to the hyperstack
							// Offset timepoint to account for negative values
							def stack_czt = metadata.getStackCZT(czt)
							def stack_position = field_image.getStackIndex(stack_czt.getC(), stack_czt.getZ(), stack_czt.getT()+1)
							// We have the position, we can now place the data
							// stacks could be 32-bit (BECAUSE REASONS)
							if( !(current_ip instanceof ij.process.ShortProcessor) ) {
								if( current_ip instanceof ij.process.FloatProcessor ) {
									IJ.log(sprintf("Image %s was 32-bit, scaled to 16-bit", current_image.image))
									current_ip.setMinAndMax( getMinDispForRescale(), getMaxDispForRescale() )
									field_stack.setProcessor(current_ip.convertToShort(true), stack_position)
								} else {
									// It's RGB or 8-bit so let's leave it at 0-255
									field_stack.setProcessor(current_ip.convertToShort(false), stack_position)
								}
							} else {
								field_stack.setProcessor(current_ip, stack_position)
							}

							current_imp.close()
						} else {
							//IJ.log("!! Got null image at "+well+" c:"+czt.getC()+" z:"+czt.getZ()+" t:"+czt.getT()+" for field:" +field+" !!")
						}
					} else {
						//IJ.log("!! No Image at "+well+" c:"+czt.getC()+" z:"+czt.getZ()+" t:"+czt.getT()+" for field:" +field+" !!")
					}
					
				}} // End Parallel Process of each plane
				
				// At this point we have a complete field in field_stack
				// Now we either copy it to the larger stack or save it
				if (isTile) {	
					checkTime( "Copying Field to Tile", {
						(1..field_stack.getSize()).each{ slice ->
							well_stack.getProcessor(slice).copyBits(field_stack.getProcessor(slice), (int) field_positions[field-1].x, (int) field_positions[field-1].y, Blitter.COPY)
						}
					})
					//print("\n--> Field "+field+" copied to tiled well #"+well+" <--")
				} else {

					// Prepare correct dimensions of image, but only if there is at least one non-singleton dimension
					if((cztDims.c+cztDims.z+cztDims.t) > 3)
						field_image = HyperStackConverter.toHyperStack(field_image, cztDims.c, cztDims.z, cztDims.t, "xyczt", "Composite")

					// Now check if we need to Z Project
					if(isProjectZ) {
						field_image = zProjectImage(field_image, getzProjectionMethod())
					}

					// add calibration
					field_image.setCalibration(theCalibration)
				
					checkTime("Saving Field", { IJ.saveAs(field_image, "Tiff", metadata.getSaveDirectory()+"//"+field_stack_name+".tif") } )
					IJ.log("\n----> Field "+field_stack_name+" saved. <----")
					field_image.close()
				}
				
			} // Done processing the fields
			
			// At this point, if this is a tile, we can save the well, or we save the position list
			if (isTile) {
			
				// Prepare correct dimensions of image, but only if there is at least one non-singleton dimension
				if((cztDims.c+cztDims.z+cztDims.t) > 3)
					checkTime( "Converting Tile to Hyperstack", { well_image = HyperStackConverter.toHyperStack(well_image, cztDims.c, cztDims.z, cztDims.t, "xyczt", "Composite") } )

				// Now check if we need to Z Project
				if(isProjectZ) {
					well_image = zProjectImage(well_image, getzProjectionMethod())
				}
				
				// add calibration # modif
				well_image.setCalibration(theCalibration)
				
				checkTime("Saving Tile", { IJ.saveAs(well_image, "Tiff", metadata.getSaveDirectory()+"//"+well_stack_name+".tif") } )
				IJ.log("\n---> Well File "+well_stack_name+" saved. <---")
				well_image.close()
				
			}
				// Save position file no matter what
				def positions_file = new File(metadata.getSaveDirectory()+"//"+well_stack_name+"-positions.txt")
				
				checkTime("Writing Position File:", {writePositionsFile(positions_file, field_stack_names, field_positions, metadata.is_z)} )
				IJ.log("\n----> Position File "+positions_file.getName()+" saved. <----")
		}} // End Parallel Process of each well
	}
	
	String confirmName(String file_name) {
		// Append data depending on the user choice
		 
		if(isSubChannels) 					    file_name+="_C"+channelsString.replaceAll(',',' ')
		if(isSubSlices) 						file_name+="_Z"+slicesString.replaceAll(',',' ')
		if(isSubTimepoints)					    file_name+="_T"+timepointsString.replaceAll(',',' ')
		if(isProjectZ && metadata.is_z )		file_name+="_ZProj_"+getzProjectionMethod()

		return file_name	
	}
	ImagePlus tryOpening(String path) {
		/*
		 * As noticed with the dataset of Cody Naricsso, there is an issue with the padding of files if there are more thatn 100 timepoints, fields or Z
		 * XML reports file URL to be r01c01f01p01-ch1sk1fk1fl1.tiff
		 * URL actually ends up being r01c01f001p01-ch1sk1fk1fl1.tiff
		 * 
		 * Idea. Match the r c f p strings with regular expressions, convert to int and find other image that matches that int combination
		 */

		ImagePlus image = IJ.openImage(path)
		if(image != null) return image
		
		// Now find which file it 'could' be
		
		def f = new File(path)
		
		// Get ID of file trying to be opened
		def gr = (f.name =~ /r(\d*)c(\d*)f(\d*)p(\d*)-ch(\d*).*/)
		def file_id = [r:gr[0][1] as int, c:gr[0][2] as int , f:gr[0][3] as int, p:gr[0][4] as int, ch:gr[0][5] as int]
		
		// All IDs and All files are defined as fields, to help this go faster
		// Find file that matches this right ID
		def idx = allIds.findIndexValues{ (it.r == file_id.r) && (it.c == file_id.c) && (it.f == file_id.f) && (it.p == file_id.p) && (it.p == file_id.p) && (it.ch == file_id.ch)}
		
		// Open it
		if(idx.size() > 0 ) {
			//IJ.log("Could not find file "+f.name+". Opening "+all_files[idx].name+" instead...");
			return IJ.openImage(allFiles[idx[0] as int].getAbsolutePath()) 
		}
		return null
		
		
	}
	
	void writePositionsFile(posfile, fileNames, positions, is_z) {
		def dim = 2
		def z= ""
		if(is_z){
			dim = 3
			z   = ", 0.0"
		}
		posfile << "#Define the number of dimensions we are working on:\n"
		posfile << "dim = "+dim+"\n"
		posfile << "# Define the image coordinates\n"
		
		fileNames.eachWithIndex{ file, i -> 
			posfile << file+".tif;      ;               ("+positions.get(i)['x']+", "+positions.get(i)['y'] + z+")\n"
		}
	}

	ImagePlus zProjectImage(ImagePlus imp, String the_method) {
		def zp = new ZProjector()
		zp.setImage(imp)
		//def methods_key = [ ZProjector.MAX_METHOD"max", "sum", "avg", "sd", "median"]
		def methods_key = [ ZProjector.MAX_METHOD, ZProjector.SUM_METHOD, ZProjector.AVG_METHOD, ZProjector.SD_METHOD, ZProjector.MEDIAN_METHOD ]
		def methods_str = ['Max Intensity', 'Sum Slices', 'Average Intensity', 'Standard Deviation', 'Median']
		def method = methods_key[methods_str.findIndexOf{it == the_method}]
		zp.setMethod(method)
		zp.setStopSlice(imp.getNSlices())
		if(imp.getNSlices() > 1 ) {
			zp.doHyperStackProjection(true) 
			return zp.getProjection()
		} else {
			//print("/n ----> Did not project. No Z slices");
			return imp
		}

	}
	/*
	 * GUI for selecting wells in case this is requested
	 */
	Boolean selectWellsGUI(int resize) {
		
		def peGUI = new SwingBuilder()
				
		def positionList = {
			peGUI.panel() {
				scrollPane(verticalScrollBarPolicy:JScrollPane.VERTICAL_SCROLLBAR_ALWAYS ) {
					list(id: "wells", 
					listData: metadata.getSelectedWells(), 
					selectionMode: ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
					)
				}
					
			}
		}
		
		def myframe = peGUI.frame(	title : 'Choose Wells', 
									location : [100, 400],
									size : [200, 300], 
									defaultCloseOperation : WindowConstants.DISPOSE_ON_CLOSE, 
									){
									panel() {
										boxLayout(axis : BoxLayout.Y_AXIS)
										label(	text : 'Select multiple with Shift or Ctrlt', 
												horizontalAlignment : JLabel.CENTER
												)
										positionList()

										button(	text : 'Run', 
											horizontalAlignment : JLabel.CENTER,
											actionPerformed : {  act ->
												selected_wells = new HashSet(peGUI.wells.getSelectedIndices().collect{ val -> metadata.getSelectedWells()[val] })
												metadata.setSelectedWells(selected_wells)
												def selproc = new Timer("Processing selected")
												selproc.tic()
												this.process(resize)
												selproc.toc()
												dispose()
											} )
										}
									}
				myframe.setVisible(true)
		}

		// super cool timer class
		def checkTime = { String name, Closure codeBlock ->
			def start = new Date().getTime()
			codeBlock()
			def end = new Date().getTime()
		
			def totalTime = end - start
			def verbage = "${totalTime}\tms"
			println "${name}\t${verbage}"
		}
	}




/*
 * Time class to 'tic-toc' a few steps and check time spent.
 */
class Timer{
	Long startTime
	Long endTime
	def name
	
	public Timer(String name){
		this.name = name
	}
	
	public void tic(){
		this.startTime = System.nanoTime()
	}
	
	public void toc(){
		this.endTime = System.nanoTime()
		IJ.log("'"+name+"' took : "+((endTime-startTime)/1e9)+" s")
	}
}


/*
 * Image class containing important imformation about each image file
 */
 @ToString(includeNames=true)
class Image {
	ArrayList<Integer> rowcol = new ArrayList<Integer>(2)
	int field
	int channel
	int slice
	int timepoint
	float posx
	float posy
	float posz
	
	int posx_px
	int posy_px
	
	float toffset
	
	String image
}

/*
 * Small class to store the CZT Indexes
 */
class CZT {
	int c
	int z
	int t
	
	CZT(int c, int z, int t) {
		this.c = c
		this.z = z
		this.t = t
	}
	
	int getC() { return c }
	int getZ() { return z }
	int getT() { return t }

	@Override 
	String toString() {
		return "("+this.c+","+this.z+","+this.t+")";
	}
}
	
/*
 * Experiment metadata that contains global information about the images and their extraction
 * Also contains helper functions to calculate all required information about the experiment
 */
 class ExperimentMetadata {
 	
	String xml_name = "Index.idx.xml"
	File parent_directory
	File save_directory
	ArrayList<Image> image_data
	def folder_files = []
	
	String experiment_name
	
	def image_size
	def pixel_size
	
	def c_xtents 
	def t_xtents
	def z_xtents
	def f_xtents
	def x_xtents
	def y_xtents
	
	def is_z
	
	def field_xy_size
	def well_xy_size
	
	def all_CZT
	
	HashSet wells
	
	/* 
	 *  Handles building the save directory
	 */
	void setParentDirectory(File dir) {
		this.parent_directory = dir
		
		//make save directory
		setSaveDirectory(new File(dir, "output"))
	}
	
	void setSaveDirectory(File saveDir) {
		this.save_directory = saveDir
		save_directory.mkdir()
	}
	// Experimen Name is used to name the final exported files
	void setExperimentName(String exp_name) { this.experiment_name = exp_name }
	
	// This method is the metadata workhorse, calculates most of what we need
	void setImageData(ArrayList<Image> image_data) {
		
		this.image_data = image_data

		//Once this is set we can calculate a bunch of useful things
		this.c_xtents = 	[ start: image_data.min{ it.channel }.channel, 	end: image_data.max{ it.channel }.channel ]
		this.t_xtents = 	[ start: image_data.min{ it.timepoint }.timepoint,	end: image_data.max{ it.timepoint }.timepoint ]
		this.z_xtents = 	[ start: image_data.min{ it.slice }.slice, 		end: image_data.max{ it.slice }.slice ]
		this.f_xtents = 	[ start: image_data.min{ it.field }.field, 		end: image_data.max{ it.field }.field ]

		//Once this is set we can calculate a bunch of useful things
		this.c_xtents = 	image_data.toUnique{it.channel}.collect{it.channel}
		this.z_xtents = 	image_data.toUnique{it.slice}.collect{it.slice}
		this.t_xtents = 	image_data.toUnique{it.timepoint}.collect{it.timepoint}
		this.f_xtents = 	image_data.toUnique{it.field}.collect{it.field}
		
		// Get extent of position in xy
		this.x_xtents = [ start: image_data.findAll{ id -> f_xtents.any{ id.field } }.min { it.posx }.posx,			end: image_data.findAll{ id -> f_xtents.any{ id.field } }.max { it.posx }.posx ]
		this.y_xtents = [ start: image_data.findAll{ id -> f_xtents.any{ id.field } }.min { it.posy }.posy,			end: image_data.findAll{ id -> f_xtents.any{ id.field } }.max { it.posy }.posy ]
		
		// Check if the dataset is 3D (for writing the positions file)
		this.is_z = (z_xtents.size() > 1 ) ? true : false
		
		// Get the size of a field
		this.field_xy_size = [ x: image_size.x , y: image_size.y ]
		
		// Size of a tiled plane is the difference of the start end xy coordinates, in pixels, to which we add the xy size of one image
		this.well_xy_size  = [x: Math.round((x_xtents.end - x_xtents.start) / pixel_size) + image_size.x, y: Math.round((y_xtents.end - y_xtents.start) / pixel_size  ) + image_size.y]
		
		// Get all Channels Slices and Timepoints
		this.all_CZT = new ArrayList<CZT>()
		(c_xtents).each{ c -> z_xtents.each{ z -> t_xtents.each{ t -> all_CZT.add(new CZT(c,z,t)) } } }
		
		this.wells = new HashSet(image_data.rowcol)
		// Compute the pixel positions of each image as well
		image_data.each{
			it.posx_px = (it.posx - x_xtents.start) / pixel_size
			it.posy_px = (y_xtents.end - it.posy )  / pixel_size
		}
	}
	
	void setFolderFiles() {
		this.parent_directory.eachFileRecurse (FileType.FILES) { file ->
			if(file.name.endsWith('.tiff'))
				this.folder_files << file
		}
	}
	
	void setImageSize(int size_x, int size_y) { this.image_size = [x: size_x, y: size_y] }
	
	void setPixelSize(double pixel_size) { this.pixel_size = pixel_size }
	
	void setSelectedWells(HashSet selection) { this.wells = selection }
	
	ArrayList<File> getFolderFiles() {
		return this.folder_files
	}
	Map getFieldSize(int resize) {
		def xy_size = this.field_xy_size
		xy_size.x /= resize
		xy_size.y /= resize
		return xy_size
	}
	
	Map getWellSize(int resize) {
		def xy_size = this.well_xy_size
		xy_size.x /= resize
		xy_size.y /= resize
		return xy_size
	}
	
	// Required for tiling by this script 
	// or for writing the positions file for downstream stitching (Grid Collection Stitching)
	Map getPixelCoordinates(int field, int resize) {
		def img = this.image_data.find { it.field == field }
		
		def px_coords = [x:img.posx_px / resize, y:img.posy_px / resize]
		return px_coords
	}
	
	// Recover data regarding final field sizes, to compute RAM usage
	// 16, for the bit depth of the camera
	// '/8' = bytes
	// '/1e9' = Gbytes
	// This will help determine the number of threads
	double getFieldStackSize(int resize) { return 16 * all_CZT.size() * field_xy_size.x * field_xy_size.y / 8 / 1e9 }
	
	double getWellStackSize(int resize) { return 16 * all_CZT.size() * well_xy_size.x  * well_xy_size.y  / 8 / 1e9 }
	
	int getFieldsPerWell() { return f_xtents.size() }
	
	int getPlanesPerField() { return all_CZT.size() }
	
	ArrayList<CZT> getAllCZT() { return this.all_CZT }

	// This fixes the fact that timepoints can be negative. This corrects the stack
	// index so that we can place it properly in an imageplus stack
	CZT getStackCZT(CZT) {
		def correctedCZT = CZT
		correctedCZT.t = correctedCZT.t - this.t_xtents.min{ it }
		return correctedCZT
	}
	
	File getXMLFile() { return new File(parent_directory, xml_name) }
	
	String getParentDirectory() { return parent_directory.getAbsolutePath() }
	
	String getSaveDirectory() { return save_directory.getAbsolutePath() }
	
	HashSet getSelectedWells() { return this.wells }
	
	String getWellName(well) { return experiment_name+" - R"+IJ.pad(well[0],2)+"-C"+IJ.pad(well[1],2) }
	
	String getFieldName(well, field) { return getWellName(well)+"-F"+IJ.pad(field,2) }
	
	Map getStackCZTDimensions() { return [c: c_xtents.size() , z: z_xtents.size(), t: t_xtents.size() ] } // T indexes start at 0
	
	Image findImage(well, czt, field) {
		return this.image_data.find { it.rowcol == well && it.channel == czt.getC() && it.timepoint == czt.getT() && it.field == field && it.slice == czt.getZ()}
	}
	
	/*
	 * xy size is straightformward but time and Z are not stored as intervals but absolute values
	 * So we need to compute their values from two subsequent frames or slices
	 */
	Calibration computeCalibration(int resize) {
		
		def z_xtents = 	(image_data.min{ it.slice }.slice)..(image_data.max{ it.slice }.slice)
		def t_xtents = (image_data.min{ it.timepoint }.timepoint)..(image_data.max{ it.timepoint }.timepoint)
		
		// Need to compute voxelDepth
		def voxel_depth = 0.0
		if(z_xtents.size() > 1) {
			def z1_image =  image_data.find { it.rowcol == image_data[0].rowcol && it.channel == image_data[0].channel && it.timepoint == image_data[0].timepoint && it.slice == z_xtents[0] }
			def z2_image =  image_data.find { it.rowcol == image_data[0].rowcol && it.channel == image_data[0].channel && it.timepoint == image_data[0].timepoint && it.slice == z_xtents[1] }
			voxel_depth = z2_image.posz - z1_image.posz	
		}
		
		// Need to compute frameInterval
		def time_delta = 1.0
		
		if(t_xtents.size() > 1) {
			def t1_image =  image_data.find { it.rowcol == image_data[0].rowcol && it.channel == image_data[0].channel && it.timepoint == t_xtents[0] && it.slice == image_data[0].slice }
			def t2_image =  image_data.find { it.rowcol == image_data[0].rowcol && it.channel == image_data[0].channel && it.timepoint == t_xtents[1] && it.slice == image_data[0].slice }
			
			time_delta = t2_image.toffset - t1_image.toffset
		}
		
		def cal = new Calibration()
		// 1e6 because values in the xml file are in meters and we want microns
		cal.pixelWidth  = pixel_size  *  1e6 * resize
		print cal.pixelWidth
		cal.pixelHeight = pixel_size  *  1e6 * resize
		cal.pixelDepth  = voxel_depth *  1e6
		
		cal.setUnit("um")
			
		cal.frameInterval = (double) time_delta
		cal.setTimeUnit("s")
		
		return cal
	}
}