package com.mikepound;

import ij.*;
import ij.gui.*;
import ij.process.*;
import ij.plugin.*;
import ij.plugin.filter.*;
import ij.plugin.frame.*;
import ij.measure.Calibration;
import ij.macro.Interpreter;
import ij.process.ShortProcessor;
import java.util.HashMap;
import ij.gui.PolygonRoi;
import ij.gui.TextRoi;
import ij.gui.Wand;
import java.awt.Polygon;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Map.Entry;
import ij.text.TextWindow;
import java.lang.Double;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashSet;
import java.io.File;
import ij.io.DirectoryChooser;
import ij.io.FileSaver;
import java.io.FileWriter;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.regex.*;

public class COLORFUL_Spotter implements PlugIn {

	public void run(String args) {
		
		// ***************************************************************************************************************
		// Choose root directory and examine files
		// ***************************************************************************************************************	
		DirectoryChooser chooser = new DirectoryChooser("Open Directory");
		String rootDirectory = chooser.getDirectory();
		
		if (rootDirectory == null)
			return;
		
		ArrayList<String> files = new ArrayList<String>();
		Set<Integer> indexes = new HashSet<Integer>();
		Set<String> fileGroups = new LinkedHashSet<String>();
		
		File dir = new File(rootDirectory);
		File[] directoryListing = dir.listFiles();
		boolean leadingZero = false;
		
		if (directoryListing != null) {
			for (File child : directoryListing) {
				try
				{
					// If child is a file, not a directory
					if (!child.isDirectory())
					{
						String path = child.toString();
						
						String extension = path.substring(path.lastIndexOf("."), path.length());

						String channelMode = "C=";
						
						if (extension.contains("tif"))
						{
							if (path.indexOf("C=") == -1)
							{
								if (path.indexOf("ch") > 0)
								{
									channelMode = "ch";
								}
								else
								{
									throw new Exception("Channel identifier C= or ch not found.");
								}
							}
							
							int cValue = -1;
							if (channelMode == "C=")
							{
								Pattern p = Pattern.compile("C=(\\d+)");
								Matcher m = p.matcher(path);
								m.find();
								String val = m.group(1);
								
								if (val.length() > 1 && val.charAt(0) == '0')
								{
									leadingZero = true;
								}
								
								cValue = Integer.parseInt(val);
								indexes.add(cValue);
								files.add(path);
								
								// Calculate raw file name without channel specific info.
								String rawFileName = m.replaceAll("<c=-channel>");
								if (!fileGroups.contains(rawFileName))
								{
									fileGroups.add(rawFileName);
								}
							}
							else 
							{
								Pattern p = Pattern.compile("ch(\\d+)");
								Matcher m = p.matcher(path);
								m.find();
								String val = m.group(1);
								
								if (val.length() > 1 && val.charAt(0) == '0')
								{
									leadingZero = true;
								}
								
								cValue = Integer.parseInt(val);
								indexes.add(cValue);
								files.add(path);
								
								// Calculate raw file name without channel specific info.
								String rawFileName = m.replaceAll("<ch-channel>");
								if (!fileGroups.contains(rawFileName))
								{
									fileGroups.add(rawFileName);
								}
							}
						}
					
					}
				}
				catch (Exception ex)
				{
					IJ.log(ex.toString());
				}
			}
			
			// ***************************************************************************************************************
			// Show dialog for specific options based on discovered files
			// ***************************************************************************************************************	
			String[] indexTitles = new String[indexes.size() + 1];
			int count = 0;
			indexTitles[count++] = "N/A";
			for (Integer i : indexes)
			{
				indexTitles[count++] = Integer.toString(i);	
			}
			
			GenericDialog gd = new GenericDialog("COLORFUL Spotter", IJ.getInstance());
			String defaultItem = indexTitles[1];
			
			gd.addChoice("Reference: ", indexTitles, defaultItem);
			gd.addChoice("Channel 1 :", indexTitles, defaultItem);
			gd.addChoice("Channel 2: ", indexTitles, defaultItem);
			gd.addNumericField("Minimum size: ", 3.0, 0);
			gd.addCheckbox("Detect in C1", true);
			gd.addCheckbox("Detect in C2", true);
			gd.addCheckbox("Apply Filters", true);
			gd.addCheckbox("Show Images", true);
			gd.addCheckbox("Show Labels", true);
			gd.addCheckbox("Show Measurement Windows", false);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
		
			int ratioIndex = gd.getNextChoiceIndex();
			if (indexTitles[ratioIndex] == "N/A") return;
			ratioIndex = Integer.parseInt(indexTitles[ratioIndex]);
			
			int c1Index = -1;
			int c1ChoiceIndex = gd.getNextChoiceIndex();
			if (indexTitles[c1ChoiceIndex] != "N/A")
			{
				c1Index = Integer.parseInt(indexTitles[c1ChoiceIndex]);
			}
				
			int c2Index = -1;
			int c2ChoiceIndex = gd.getNextChoiceIndex();
			if (indexTitles[c2ChoiceIndex] != "N/A")
			{
				c2Index = Integer.parseInt(indexTitles[c2ChoiceIndex]);
			}
			
			if (c1Index == -1 && c2Index == -1) return;
			
			int minimumPixelSize = (int)gd.getNextNumber();
			
			boolean channel1Detect = gd.getNextBoolean();
			boolean channel2Detect = gd.getNextBoolean();
			
			boolean applyFilter = gd.getNextBoolean();
			boolean showImages = gd.getNextBoolean();
			boolean showLabels = gd.getNextBoolean();
			boolean showMeasurementWindows = gd.getNextBoolean();

			try
			{
				// Output directory and measurement output
				String outputDirectory = rootDirectory.toString() + "output\\";
				File outputFileDirectory = new File(outputDirectory);
				if(!outputFileDirectory.exists())
				{
					outputFileDirectory.mkdirs();	
				}
				
				FileWriter writer = new FileWriter(outputFileDirectory + "\\measurements.csv", false);
				
				// Process the groups of images using the channels specified
				for (String rawPath : fileGroups)
				{	
					// Determine the form of these files
					String filter = rawPath.indexOf("<c=-channel>") > 0 ? "<c=-channel>" : "<ch-channel>";
					String output = filter == "<c=-channel>" ? "C=" : "ch";
					if (leadingZero) output = output + "0";
					
					String ratioFileName = rawPath.replace(filter, output + Integer.toString(ratioIndex));
					String c1FileName = rawPath.replace(filter, output + Integer.toString(c1Index));
					String c2FileName = rawPath.replace(filter, output + Integer.toString(c2Index));
					String cleanPath = rawPath.replace(" " + filter + " ","");
					String cleanName = cleanPath.substring(cleanPath.lastIndexOf("\\") + 1);
					String cleanNameWithoutExtension = cleanName.substring(0, cleanName.length() - 4);
					
					writer.write(cleanNameWithoutExtension + "\n");
					
					// ***************************************************************************************************************
					// Image Loading, Filtering and Thresholding
					// ***************************************************************************************************************
									
					// Load images if available
					ImagePlus ratioImage = new ImagePlus(ratioFileName);
					ImagePlus c1Image = null, c2Image = null;
					if (c1Index >= 0) c1Image = new ImagePlus(c1FileName);
					if (c2Index >= 0) c2Image = new ImagePlus(c2FileName);
					
					ImageProcessor ratioIP = ratioImage.getProcessor();
					ImageProcessor c1IP = null, c2IP = null;
					if (c1Image != null) c1IP = c1Image.getProcessor();
					if (c2Image != null) c2IP = c2Image.getProcessor();

					ImagePlus target = IJ.createImage("Addition " + cleanPath,
												ratioImage.getWidth(),
												ratioImage.getHeight(),
												1,
												8);
					ImageProcessor targetIP = target.getProcessor();

					targetIP.copyBits(ratioIP, 0, 0, Blitter.ADD);
					if (c1Image != null && channel1Detect) targetIP.copyBits(c1IP, 0, 0, Blitter.ADD);
					if (c2Image != null && channel2Detect) targetIP.copyBits(c2IP, 0, 0, Blitter.ADD);

					// Apply median filter
					if (applyFilter) IJ.run(target, "Median...", "radius=1");

					// Apply threshold
					IJ.run(target, "Auto Threshold", "method=Otsu white");

					// ***************************************************************************************************************
					// Connected components
					// ***************************************************************************************************************				
					ShortProcessor map = new ShortProcessor(ratioImage.getWidth(), ratioImage.getHeight());
					int size = ratioImage.getWidth() * ratioImage.getHeight();
					int numberOfLabels = doLabels4(targetIP, map, 0, false);
					Wand wand = new Wand(map);
					Overlay overlay= new Overlay();
					
					HashMap<Integer,int[]> vcoords=new  HashMap<Integer,int[]>(100);
					int width = ratioImage.getWidth();
					int height = ratioImage.getHeight();
					for (int i=0; i< size; i++) {
						if (map.get(i)>0) {
								final int x=i%width; 
								final int y=i/width; 
								vcoords.put(i,new int[]{x,y});		
						}
					}

					ArrayList<PolygonRoi> rois = new ArrayList<PolygonRoi>();

					int fontSize = 12;
					Font f = new Font("Arial", Font.PLAIN, fontSize);
					
					for (int k=1; k<=numberOfLabels; k++) {
						Polygon p= getContour3(map,k, vcoords, wand);
						if (p!=null) {
							PolygonRoi roi=new PolygonRoi(p, Roi.POLYGON);
							try {

								// Verify area of polygon
								int n = p.npoints;
								int[] xs = p.xpoints;
								int[] ys = p.ypoints;

								double area = PolygonArea(xs, ys, n);
								
								if (area >= minimumPixelSize)
								{
									overlay.add(roi);
									
									rois.add(roi);
									
									if (showLabels)
									{
										Rectangle r = roi.getBounds();
										double x = r.getX() + r.getWidth();
										double y = r.getY() + r.getHeight();
										if (x + fontSize > width || y + fontSize > height)
										{
											x -= (r.getWidth() + fontSize);
											y -= (r.getHeight() + fontSize);
										}
										
										TextRoi text = new TextRoi(x, y, String.valueOf(rois.indexOf(roi)), f);
										
										overlay.add(text);
									}	
									
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
					
					ImagePlus finalImage = new ImagePlus(cleanPath, map);
					finalImage.setOverlay(overlay);
					
					// Save thresholded image
					ImagePlus imp2 = finalImage.flatten();
					FileSaver saver = new FileSaver(imp2);
					saver.saveAsTiff(outputFileDirectory + "\\" +  cleanName);
					
					// ***************************************************************************************************************
					// Measurements and file output
					// ***************************************************************************************************************
					Calibration cal = ratioImage.getCalibration();

					double[][] meanStatistics = new double[rois.size()][3];
					double[] areaStatistics = new double[rois.size()];
					int pos = 0;
					for (PolygonRoi r : rois)
					{
						// Set roi to processors
						ratioIP.setRoi(r);
						
						if (c1Image != null) c1IP.setRoi(r);
						if (c2Image != null) c2IP.setRoi(r);

						ImageStatistics ratioStats = ImageStatistics.getStatistics(ratioIP, ImageStatistics.MEAN, cal);
						meanStatistics[pos][0] = ratioStats.mean;
						
						if (c1Image != null)
						{
							ImageStatistics channel1Stats = ImageStatistics.getStatistics(c1IP, ImageStatistics.MEAN, cal);
							meanStatistics[pos][1] = channel1Stats.mean;
						}
						
						if (c2Image != null)
						{
							ImageStatistics channel2Stats = ImageStatistics.getStatistics(c2IP, ImageStatistics.MEAN, cal);
							meanStatistics[pos][2] = channel2Stats.mean;
						}
						
						areaStatistics[pos] = ratioStats.pixelCount;

						pos++;

						// Clear roi
						ratioIP.resetRoi();
						if (c1Image != null) c1IP.resetRoi();
						if (c2Image != null) c2IP.resetRoi();	    	
					}
					
					TextWindow tw = null;
					if (showMeasurementWindows) {
						tw = new TextWindow(cleanPath, "Index\tReference\tChannel1\tChannel2\tC1/R\tC2/R\tArea", "", 450,450);
					}
					
					writer.write("Index,Reference,Channel1,Channel2,C1/R,C2/R,Area\n");
					
					// N C1 C2 C1N C2N
					double[] averageStats = new double[5];
					double totalArea = 0.0;
					
					for (int i = 0; i < rois.size(); i++)
					{
						double ratio1 = Double.POSITIVE_INFINITY, ratio2 = Double.POSITIVE_INFINITY;
						
						if (meanStatistics[i][0] > 0)
						{
							ratio1 = meanStatistics[i][1] / meanStatistics[i][0];
							ratio2 = meanStatistics[i][2] / meanStatistics[i][0];
						}
					
						double area = areaStatistics[i];
						double norm = round(meanStatistics[i][0], 2, BigDecimal.ROUND_HALF_UP);
						String resultsLine = String.valueOf(i) + "\t" + String.valueOf(norm);
						averageStats[0] += norm;
						
						if (c1Image != null)
						{
							resultsLine += "\t" + round(meanStatistics[i][1], 2, BigDecimal.ROUND_HALF_UP);
							averageStats[1] += meanStatistics[i][1];
						}
						else resultsLine += "\tN/A";
						
						if (c2Image != null)
						{
							resultsLine += "\t" + round(meanStatistics[i][2], 2, BigDecimal.ROUND_HALF_UP);
							averageStats[2] += meanStatistics[i][2];
						}
						else resultsLine += "\tN/A";
						
						if (c1Image != null)
						{
							resultsLine += "\t" + round(ratio1, 2, BigDecimal.ROUND_HALF_UP);
							averageStats[3] += ratio1;
						}
						else resultsLine += "\tN/A";
					
						if (c2Image != null)
						{
							resultsLine += "\t" + round(ratio2, 2, BigDecimal.ROUND_HALF_UP);
							averageStats[4] += ratio2;
						}
						else resultsLine += "\tN/A";
					
						resultsLine += "\t" + Math.round(areaStatistics[i]);
						totalArea += area;
						
						if (showMeasurementWindows)
							tw.append(resultsLine);
						
						writer.write(resultsLine.replace("\t",",") + "\n");
					}
				
					String averageResults = String.valueOf("Avg:\t" + round(averageStats[0] / rois.size(), 2, BigDecimal.ROUND_HALF_UP))
					 + "\t" + String.valueOf(round(averageStats[1] / rois.size(), 2, BigDecimal.ROUND_HALF_UP))
					 + "\t" + String.valueOf(round(averageStats[2] / rois.size(), 2, BigDecimal.ROUND_HALF_UP))
					 + "\t" + String.valueOf(round(averageStats[3] / rois.size(), 2, BigDecimal.ROUND_HALF_UP))
					 + "\t" + String.valueOf(round(averageStats[4] / rois.size(), 2, BigDecimal.ROUND_HALF_UP))
					 + "\t" + String.valueOf(round(totalArea / (double)rois.size(), 2, BigDecimal.ROUND_HALF_UP));

					if (showMeasurementWindows)
						tw.append(averageResults);
					
					writer.write("\n" + averageResults.replace("\t",",") + "\n\n");
					
					if (showImages)
					{
						finalImage.show();				
					}
				}
				
				writer.close();
			}
			catch(java.io.IOException ex)
			{
			}
	
		}

	}

	public static double round(double unrounded, int precision, int roundingMode)
	{
	    BigDecimal bd = new BigDecimal(unrounded);
	    BigDecimal rounded = bd.setScale(precision, roundingMode);
	    return rounded.doubleValue();
	}

	public static double PolygonArea(int[] xs, int[] ys, int N) {

		int i, j;
		double area = 0;

		for (i = 0; i < N; i++) {
			j = (i + 1) % N;
			area += xs[i] * ys[j];
			area -= ys[i] * xs[j];
		}

		area /= 2.0;
		return (Math.abs(area));
	}

	private int doLabels4(ImageProcessor bp, ShortProcessor map, int bgcol, boolean edgecorr) {
		
		if (bp instanceof ColorProcessor)
			return -1;
			
		final int width=bp.getWidth();
		final int height=bp.getHeight();
		final int size=width*height;
		
		final int mwidth=map.getWidth();
		final int mheight=map.getHeight();
		
		if (width!=mwidth || height!= mheight)
			throw new IllegalArgumentException ("dimensions mismatch");
			
		if (edgecorr) {
			for (int a=0;a<mwidth; a++) {
				bp.set(a, 0, bgcol);
			}

			for (int a=0;a<mheight; a++) {
				bp.set(0, a, bgcol);
			}
		}
		
		int [] labels  = new int[size/2];
		
		for (int i=0; i<labels.length; i++) {
			labels[i]=i ; // ramp
		}
		
		int[] nbs= new int[2];
		int[] nbslab= new int[2];
		
		int numberOfLabels =1;
		int labelColour=1; // background
		
		int result=0;
		
		for(int y=0; y<height; y++) {	 
			//labelColour=0;
			for(int x=0; x<width; x++){		
				final int val=bp.get(x, y);				
			      if( val == bgcol ){
			    	  result = 0;  //nothing here
			      } else {

			    	  //The 4 connected visited neighbours
			    	  neighborhood4(bp, nbs, x, y, width);
			    	  neighborhood4(map, nbslab, x, y, width);

			    	  //label the point
			    	  if( (nbs[0] == nbs[1]) && (nbs[0] == bgcol )) { 
			    		  // all neighbours are 0 so gives this point a new label
			    		  result = labelColour;
			    		  labelColour++;
			    	  } else { //one or more neighbours have already got labels

			    		  int count = 0;
			    		  int found = -1;
			    		  for( int j=0; j<nbs.length; j++){
			    			  if( nbs[ j ] != bgcol ){
			    				  count +=1;
			    				  found = j;
			    			  }
			    		  }
			    		  if( count == 1 ) {
			    			  // only one neighbour has a label, so assign the same label to this.
			    			  result = nbslab[ found ];
			    		  } else {
			    			  // more than 1 neighbour has a label
			    			  result = nbslab[ found ];
			    			  // Equivalence the connected points
			    			  for( int j=0; j<nbslab.length; j++){
			    				  if( ( nbslab[ j ] != 0 ) && (nbslab[ j ] != result ) ){
			    					  associate(labels, nbslab[ j ], result );
			    				  } // end if
			    			  } // end for
			    		  } // end else
			    		  
			    	  } // end else
			    	  map.set(x, y, result);
			      } // end if			    
			} // end for
		} // end for
		
		// Reduce equivalent labels
		System.out.println(" labels " + labelColour);
		for( int i= labels.length -1; i > 0; i-- ){
			labels[ i ] = reduce(labels, i );
		}

		/*now labels will look something like 1=1 2=2 3=2 4=2 5=5.. 76=5 77=5
			      this needs to be condensed down again, so that there is no wasted
			      space eg in the above, the labels 3 and 4 are not used instead it jumps
			      to 5.
		 */
		if (labelColour>0) {
		int condensed[] = new int[ labelColour ]; // can't be more than nextlabel labels
		
		int count = 0;
		for (int i=0; i< condensed.length; i++){
			if( i == labels[ i ] ) 
				condensed[ i ] = count++;
		}

		numberOfLabels = count -1;
		 
		// now run back through our preliminary results, replacing the raw label
		// with the reduced and condensed one
	    for (int i=0; i< size; i++){
	    	int val=map.get(i);
	    	val = condensed[ labels[ val ] ];	        
	    	map.set(i, val);
	    }
			return numberOfLabels;
		} else {
			return -1;
		}
	}

	private void neighborhood4(ImageProcessor bp, int[] nbs, int x, int y, int width) {
		if ( x <= 0 ) x=1;
		if ( x >= width ) x=width-1;
		if ( y <= 0 ) y=1;
		nbs[0]=bp.get(x-1,y); // west
		nbs[1]=bp.get(x,y-1); // south
	}

	private void associate(int[] labels, int a, int b ) {	    
	    if( a > b ) {
	      associate(labels, b, a );
	      return;
	    }
	    if( ( a == b ) || ( labels[ b ] == a ) ) return;
	    if( labels[ b ] == b ) {
	      labels[ b ] = a;
	    } else {
	      associate(labels, labels[ b ], a );
	      if (labels[ b ] > a) {             //***rbf new
	        labels[ b ] = a;
	      }
	    }
	}
	  
	  private int reduce(int[] labels, int a ){
	    
	    if (labels[a] == a ){
	      return a;
	    } else {
	      return reduce(labels, labels[a] );
	    }
	  }

private Polygon getContour3(ShortProcessor map,int label, 
			HashMap<Integer,int[]> coords, Wand wand) {
		final int width=map.getWidth();
		final int height=map.getHeight();
		int sz=width*height;
		LinkedHashMap<Integer,ArrayList<int[]>> cmap= new LinkedHashMap<Integer,ArrayList<int[]>> (100);
	
		//System.out.print("<< " +label+" \n");
		
		coords.entrySet();
		for (int i=0; i<sz; i++ ) {
			int key=map.get(i); 
			if (key==label) {
				distribute(i,   width,  cmap);
					
			} // end if
			
		} // end for
		//System.out.print("\n "+cnt+" >> \n");
		Set<Entry<Integer, ArrayList<int[]>>> entries=cmap.entrySet();
		//Iterator<Entry<Integer, ArrayList<int[]>>> iter=entries.iterator();
		int key=0;
		boolean first=false;
		for (Entry<Integer, ArrayList<int[]>> e:entries) {
			//ArrayList<int[]> value=e.getValue();
			//System.out.print(e.getKey() + ">>");
			if (!first) {
				key=e.getKey();
				break;
			}
			first=true;
			
		/*	for (int[] k: value) {
				System.out.print("("+k[0]+" "+ k[1]+"),");
			}
			System.out.print(" <<\n");
			*/
		}
		
		 		
		try {
			
			Polygon poly=new Polygon();
			ArrayList<int[]> aux=cmap.get(key);
			if (aux!=null) {
				int[] c=aux.get(0);
				int startX=c[0];
				int startY=c[1];
				//System.out.println(key +" -> ("+startX+" "+ startY+")");
				wand.autoOutline(startX, startY, label, label+1);		
				poly.xpoints=wand.xpoints;
				poly.ypoints=wand.ypoints;
				poly.npoints=wand.npoints;
				return poly;
			}  
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	final int maxd=2; // maximal metrical radius of a "circle"
	private void distribute(int idx, int width,  LinkedHashMap<Integer,ArrayList<int[]>> cmap) {
		int[] c=new int[2];		
		c[0]=idx % width;
		c[1]=idx / width;
		
		for (Entry<Integer, ArrayList<int[]>> e:cmap.entrySet()) {
			ArrayList<int[]> clist=e.getValue();
			final int lastind=clist.size()-1;
			final int[] ctail=clist.get(lastind);
			final int[] chead=clist.get(0);
			int dist= dist (ctail,c); 
			if (dist<maxd ) {
				clist.add(c);
				return;
			}
			dist= dist (chead,c); 
			if (dist<maxd ) {
				clist.add(0,c);
				return;
			}
			
		}
		ArrayList<int[]> alist=new ArrayList<int[]>(100);
		alist.add(c);
		cmap.put(idx, alist);
	}
	
	private int dist(int[] u, int[] v) {
		final int d= Math.max(Math.abs(u[0]-v[0]) , Math.abs(u[1]-v[1]));
		return d;
	}


}