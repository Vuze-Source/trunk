<?php
	$latestVersion = 3052;

	if(! isset($platform) || $platform == '')
		$platform 	= @$_GET['platform'];
	
	if(! isset($platform) || $platform == '')
		exit();
	
	echo($latestVersion . "\n");

	if($platform == "win32") { ?>
http://mirror.tiscali.dk/eclipse/downloads/drops/S-3.0M9-200405211200/swt-3.0M9-win32.zip
http://download2.eclipse.org/downloads/drops/S-3.0M9-200405211200/swt-3.0M9-win32.zip
<?php } else if($platform == "motif") { ?>
http://mirror.tiscali.dk/eclipse/downloads/drops/S-3.0M9-200405211200/swt-3.0M9-linux-motif.zip
http://download2.eclipse.org/downloads/drops/S-3.0M9-200405211200/swt-3.0M9-linux-motif.zip
<?php } else if($platform == "gtk") { ?>
http://mirror.tiscali.dk/eclipse/downloads/drops/S-3.0M9-200405211200/swt-3.0M9-linux-gtk.zip
http://download2.eclipse.org/downloads/drops/S-3.0M9-200405211200/swt-3.0M9-linux-motif.zip
<?php } else if($platform == "carbon") { ?>
http://mirror.tiscali.dk/eclipse/downloads/drops/S-3.0M9-200405211200/swt-3.0M9-macosx-carbon.zip
http://download2.eclipse.org/downloads/drops/S-3.0M9-200405211200/swt-3.0M9-macosx-carbon.zip
<?php } ?>