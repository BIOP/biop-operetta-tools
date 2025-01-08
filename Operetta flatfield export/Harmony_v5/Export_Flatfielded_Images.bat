:: Batch file to start the Export Flatfielded Images Acapella Script
:: This is compatible with Harmony 5.1-5.2
::
:: Please uncomment (remove the :: ) the line that matches your installation.
::
:: There are some options, that can be specified on the command call. 
:: Options are added between 
::        Acapella.exe" 
:: and
::        Export_Flatfielded
::
::
:: To run the script without user interaction from command line you can add the file name
:: of the index.xml:
::        -s FileName="C:\temp\index.xml" 
::
:: To specify a different Prefix (e.g. "flex_") for the file names you can add:
::        -s ResultPrefix=flex_
:: or to remove the Prefix specify:
::        -s ResultPrefix= 
::
:: By default the images are "LZW" compressed TIFF images (standard tiff images!). Should you
:: require that there is no compresion, specify:
::        -s CompString=none


ECHO OFF
ECHO:
:: For Harmony 5.1
:: "%programfiles%\PerkinElmer\Harmony 5.1\ACServ\Acapella.exe" Export_Flatfielded_Images.script

:: For Harmony 5.2 (PerkinElmer)
:: "%programfiles%\PerkinElmer\Harmony 5.2\ACServ\Acapella.exe" Export_Flatfielded_Images.script

:: For Harmony 5.2.2 (Revvity)
 "%programfiles%\Revvity\Harmony 5.2\ACServ\Acapella.exe" -s ResultPrefix=  Export_Flatfielded_Images.script


PAUSE
