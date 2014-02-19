/******************************************************************************
	This file is part of the AnnotationConverter, Physio-MIMI Application tools 

    AnnotationConverter is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    AnnotationConverter is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
    
    Copyright 2010, Case Western Reserve University
*******************************************************************************/
package translator.logic;
//import java.io.File;
import java.io.*;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.xerces.dom.DocumentImpl;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;

public class AnnotationConverter {

	public String[] readEDF(String edfFile){
		String[] startDate = new String[2];
		SimpleDateFormat df = new SimpleDateFormat ("mm.dd.yyyy hh.mm.ss");
		try {
			RandomAccessFile edf = new RandomAccessFile(new File(edfFile),"r");
			edf.seek(168);
			char[] date = new char[8];
			for (int i =0; i<8; i++){
				date[i] = (char) edf.readByte();
			}
			
			//edf.read(date);
			char[] time = new char[8];
			for (int i =0; i<8; i++){
				time[i] = (char) edf.readByte();
			}
			edf.seek(236);
		
			char[] numRec = new char[8];
			for (int i=0; i<8; i++){
				numRec[i] = (char) edf.readByte();
				//System.out.println(dur[i]);
			}
			char[] durRec = new char[8];
			for (int i=0; i<8; i++){
				durRec[i] = (char) edf.readByte();
				//System.out.println(dur[i]);
			}
			
			//long numRec = edf.readLong();
			///long durRec = edf.readLong();
			long duration = Long.parseLong(String.valueOf(durRec).trim()) * Long.parseLong(String.valueOf(numRec).trim());
			//long duration = 0;
			//edf.read(time);
			startDate[0] = String.valueOf(date) + " " + String.valueOf(time);
			startDate[1] = String.valueOf(duration);
			edf.close();
		} catch (Exception e){
			e.printStackTrace();
			
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log(errors.toString());
		}
		return startDate;
	}
	public HashMap[] readMapFile(String mapFile){
		HashMap[] map = new HashMap[3];
		//HashMap map = new HashMap();
		try{
			BufferedReader input =  new BufferedReader(new FileReader(mapFile));
			String line = input.readLine();
			//line = input.readLine();
			HashMap epoch = new HashMap();
			//String[] data = line.split(",");
			//epoch.put("EpochLength",data[2]);
			//map[0]=epoch;
			HashMap events = new HashMap();
			HashMap stages = new HashMap();
			while ((line = input.readLine())!=null){
				String[] data = line.split(",");
				if (data[0].compareTo("EpochLength")!=0 && data[0].compareTo("Sleep Staging")!=0){
					ArrayList<String> values = new ArrayList<String>(3);
					values.add(data[0]);
					values.add(data[2]);
					if (data.length>=4){
						values.add(data[3]);
					}
					events.put(data[1], values);
				} else if (data[0].compareTo("EpochLength")==0){
					//System.out.println(data[0]);
					epoch.put(data[0], data[2]);
				} else {
					stages.put(data[1], data[2]);
				}

			}	
			//System.out.println(map[2].values().size());
			input.close();
			map[0]=epoch;
			map[1]=events;
			map[2]=stages;
		} catch(IOException e){
			e.printStackTrace();
			
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log(errors.toString());
		}
		return map;
	}
	public Element addElements(Document doc, String[] elements){
		Element eventsElmt = doc.createElement("ScoredEvent");
		Element nameElmt = doc.createElement("EventConcept");
		nameElmt.appendChild(doc.createTextNode(elements[0]));
		eventsElmt.appendChild(nameElmt);
		Element startElmt = doc.createElement("Start");
		startElmt.appendChild(doc.createTextNode(elements[1]));
		eventsElmt.appendChild(startElmt);
		Element durationElmt = doc.createElement("Duration");
		durationElmt.appendChild(doc.createTextNode(elements[2]));
		eventsElmt.appendChild(durationElmt);
		return eventsElmt;
	}
	
	public void saveXML(Document xml, String filename){
		try {
			String tarfileDir = filename.substring(0, filename.lastIndexOf(File.separator));
			File f1 = new File(tarfileDir);
			if (!f1.exists()){
				f1.mkdirs();
			}
			
			File f2 = new File(filename);
			FileOutputStream fos = new FileOutputStream(filename, f2.exists());
			// XERCES 1 or 2 additionnal classes.
			OutputFormat of = new OutputFormat("XML","ISO-8859-1",true);
			of.setIndent(1);
			of.setIndenting(true);
			//of.setDoctype(null,"users.dtd");
			XMLSerializer serializer = new XMLSerializer(fos,of);
			// As a DOM Serializer
			serializer.asDOMSerializer();
			serializer.serialize( xml.getDocumentElement() );
			//System.out.println(outfile);
			fos.close();
		} catch(IOException e){
			e.printStackTrace();
			
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log(errors.toString());
		}
	}
	
	public void log(String info){
		TranslationController.translationErrors += info;
	}
	
	public boolean convertXML(String annotation_file, String edf_file, String mapping_file, String output_file){
		
		HashMap[] map = this.readMapFile(mapping_file);
		ArrayList<String> events = new ArrayList<String>(map[1].keySet().size());
		double[] starttimes = new double[map[1].keySet().size()];
		Document xml = new DocumentImpl();
		Element root = xml.createElement("PSGAnnotation");
		Element software = xml.createElement("SoftwareVersion");
		software.appendChild(xml.createTextNode("Compumedics"));
		Element epoch = xml.createElement("EpochLength");
		epoch.appendChild(xml.createTextNode((String) map[0].get("EpochLength")) ) ;
		root.appendChild(software);
		root.appendChild(epoch);
		Element eventsElmt = xml.createElement("ScoredEvents");
		String[] timeStr = readEDF(edf_file);
		String[] elmts = new String[3];
		elmts[0] = "Recording Start Time";
		elmts[1] = "0";
		elmts[2] = timeStr[1];
		Element elmt = addElements(xml,elmts);
		Element clock = xml.createElement("ClockTime");
		clock.appendChild(xml.createTextNode(timeStr[0]));
		elmt.appendChild(clock);
		eventsElmt.appendChild(elmt);
		boolean bTranslation = true;
		
		InputStream inputStream = null;
		try {
			//http://stackoverflow.com/questions/1772321/what-is-xml-bom-and-how-do-i-detect-it
			//Detect Byte order mark
			inputStream = new FileInputStream(new File(annotation_file));
			@SuppressWarnings("resource")
			BOMInputStream bOMInputStream = new BOMInputStream(inputStream);
			ByteOrderMark bom = bOMInputStream.getBOM();
			String charsetName = bom == null ? "UTF-8" : bom.getCharsetName();
			inputStream.close();
			
			inputStream = new FileInputStream(new File(annotation_file));
			Reader reader = new InputStreamReader(inputStream, charsetName);
			InputSource is = new InputSource(reader);
			is.setEncoding(charsetName);
			
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(is);
			doc.getDocumentElement().normalize();
			NodeList nodeLst = doc.getElementsByTagName("ScoredEvent");
			
//			File file = new File(annotation_file);
//			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
//			DocumentBuilder db = dbf.newDocumentBuilder();
//			Document doc = db.parse(file);
//			doc.getDocumentElement().normalize();
//			NodeList nodeLst = doc.getElementsByTagName("ScoredEvent");
			
			for (int s = 0; s < nodeLst.getLength(); s++) {
				Element e = null;
				Node n = null;
				Node fstNode = nodeLst.item(s);
				Element fstElmnt = (Element) fstNode;
				NodeList fstNmElmntLst = fstElmnt.getElementsByTagName("Name");
				Element fstNmElmnt = (Element) fstNmElmntLst.item(0);
				NodeList fstNm = fstNmElmnt.getChildNodes();

				String eventname = ((Node) fstNm.item(0)).getNodeValue();
				if ( map[1].keySet().contains(eventname)) {
					e = xml.createElementNS(null,"ScoredEvent");
					Element name = xml.createElement("EventConcept");
					Node nameNode = xml.createTextNode((String) ((ArrayList) map[1].get(eventname)).get(1));
					name.appendChild(nameNode);
					e.appendChild(name);


					//System.out.println("\t<EventConcept>" + map[1].get(eventname) + "</EventConcept>");
					NodeList lstNmElmntLst = fstElmnt.getElementsByTagName("Duration");
					Element lstNmElmnt = (Element) lstNmElmntLst.item(0);
					NodeList lstNm = lstNmElmnt.getChildNodes();

					Element duration = xml.createElement("Duration");
					Node durationNode = xml.createTextNode((String) ((Node) lstNm.item(0)).getNodeValue());
					duration.appendChild(durationNode);
					e.appendChild(duration);

					//System.out.println("\t<Duration>" + ((Node) lstNm.item(0)).getNodeValue() + "</Duration>" );
					NodeList startElmntLst = fstElmnt.getElementsByTagName("Start");
					Element startElmnt = (Element) startElmntLst.item(0);
					NodeList start = startElmnt.getChildNodes();
					double starttime= Double.parseDouble(((Node) start.item(0)).getNodeValue());
					Element startEt = xml.createElement("Start");
					Node startNode = xml.createTextNode(Double.toString(starttime));
					startEt.appendChild(startNode);
					e.appendChild(startEt);
					if (((ArrayList<String>) map[1].get(eventname)).get(0).compareTo("Desaturation")==0){
						//System.out.println("here");
						NodeList otherElmntLst = fstElmnt.getElementsByTagName("LowestSpO2");
						double lowestspo2 = 0;
						if (otherElmntLst.getLength()>=1){
							Element lowest = (Element) otherElmntLst.item(0);
							Element nadir = xml.createElement("SpO2Nadir");
							NodeList nadirLst = lowest.getChildNodes();
							nadir.appendChild(xml.createTextNode(nadirLst.item(0).getNodeValue()));
							lowestspo2 = Double.parseDouble(nadirLst.item(0).getNodeValue());
							e.appendChild(nadir);
						}
						NodeList baseLst = fstElmnt.getElementsByTagName("Desaturation");
						if (baseLst.getLength()>=1){
							Element baseElmnt = (Element) baseLst.item(0);
							Element baseline = xml.createElement("SpO2Baseline");
							NodeList baselineLst = baseElmnt.getChildNodes();
							baseline.appendChild(xml.createTextNode(Double.toString(Double.parseDouble(baselineLst.item(0).getNodeValue()) + lowestspo2)));
							e.appendChild(baseline);
						}
					}
				
					// Other informations depending on type of events
					if (((ArrayList<String>) map[1].get(eventname)).get(0).compareTo("Respiratory") == 0){
						
					}
					if (((ArrayList) map[1].get(eventname)).size() > 2){
						Element notes = xml.createElement("Notes");
						notes.appendChild(xml.createTextNode( ((ArrayList<String>) map[1].get(eventname)).get(2) ));
						e.appendChild(notes);
					}
					
					eventsElmt.appendChild(e);

				} else {

					Element eventNode = xml.createElement("ScoredEvent");
					Element nameNode = xml.createElement("EventConcept");
					Element startNode = xml.createElement("Starttime");
					Element durationNode = xml.createElement("Duration");
					Element notesNode = xml.createElement("Notes");
					
					nameNode.appendChild(xml.createTextNode("Technician Notes"));
					notesNode.appendChild(xml.createTextNode(eventname));
					NodeList lstNmElmntLst = fstElmnt.getElementsByTagName("Duration");
					Element lstNmElmnt = (Element) lstNmElmntLst.item(0);
					NodeList lstNm = lstNmElmnt.getChildNodes();

					Element duration = xml.createElement("Duration");
					Node durationN = xml.createTextNode((String) ((Node) lstNm.item(0)).getNodeValue());
					durationNode.appendChild(durationN);
					//e.appendChild(duration);

					//System.out.println("\t<Duration>" + ((Node) lstNm.item(0)).getNodeValue() + "</Duration>" );
					NodeList startElmntLst = fstElmnt.getElementsByTagName("Start");
					Element startElmnt = (Element) startElmntLst.item(0);
					NodeList start = startElmnt.getChildNodes();
					double starttime= Double.parseDouble(((Node) start.item(0)).getNodeValue());
					//Element startEt = xml.createElement("Start");
					Node startN = xml.createTextNode(Double.toString(starttime));
					startNode.appendChild(startN);
					
					eventNode.appendChild(nameNode);
					eventNode.appendChild(startNode);
					eventNode.appendChild(durationNode);
					eventNode.appendChild(notesNode);
					
					eventsElmt.appendChild(eventNode);
					String info = annotation_file + "," + eventname + "," + Double.toString(starttime) ;
					this.log(info);
					
				}
				
			}
			//Sleep stages
			NodeList allStages = doc.getElementsByTagName("SleepStage");
			
			Element eventNode = xml.createElement("ScoredEvent");
			Element nameNode = xml.createElement("EventConcept");
			Element startNode = xml.createElement("Start");
			Element durationNode = xml.createElement("Duration");
			
			String stage = ((Element) allStages.item(0)).getTextContent();
			String name = "";
			if (map[2].keySet().contains(stage)) {
				name = (String) map[2].get(stage);
			}
				double start = 0;
				nameNode.appendChild(xml.createTextNode(name));
				startNode.appendChild(xml.createTextNode(Double.toString(start)));
				eventNode.appendChild(nameNode);
				eventNode.appendChild(startNode);
				//eventNode.appendChild(durationNode);
				//eventsElmt.appendChild(eventNode);
				//System.out.println(name);
			
			int count =0;
			for (int i=1; i< allStages.getLength(); i++) {
				String nstage = ((Element) allStages.item(i)).getTextContent();
			    if (nstage.compareTo(stage) == 0){
			        count = count +1;
			    } else {
			        durationNode.appendChild(xml.createTextNode(Double.toString(count*30)));
			        eventNode.appendChild(durationNode);
			        eventsElmt.appendChild(eventNode);
			        eventNode = xml.createElement("ScoredEvent");
			        nameNode = xml.createElement("EventConcept");
			        stage = nstage;
			        
			        if (map[2].keySet().contains(stage)){
			        	name = (String) map[2].get(stage);
					}
			        nameNode.appendChild(xml.createTextNode(name));
			        startNode = xml.createElement("Start");
			        start = count*30 + start;
			        startNode.appendChild(xml.createTextNode(Double.toString(start)));
			        durationNode = xml.createElement("Duration");
			        //durationNode.appendChild(xml.createTextNode("abc"));
			        //durationNode.appendChild(xml.createTextNode(Integer.toString(count*30)));
			        eventNode.appendChild(nameNode);
			        eventNode.appendChild(startNode);
			        //eventNode.appendChild(durationNode);
			        count = 1;
				}
			}
			durationNode.appendChild(xml.createTextNode(Double.toString(count*30)));
			eventNode.appendChild(durationNode);
			eventsElmt.appendChild(eventNode);

			//root.appendChild(eventsElmt);
			
		} catch (Exception e) {
			e.printStackTrace();
			bTranslation = false;
			
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log(errors.toString());
		}
		finally{
			try{
				if (inputStream !=  null)
					inputStream.close();
			}
			catch(Exception e){
			}
		}
		root.appendChild(eventsElmt);
		xml.appendChild(root);
		saveXML(xml,output_file);
		//System.out.println(outfile.get);
		return bTranslation;

	}

	public boolean convertTXT(String annotation_file, String mapping_file, String output_file){
		//System.out.println(outfile);
		HashMap[] map = readMapFile(mapping_file);
		Document doc = new DocumentImpl();
		Element root = doc.createElement("PSGAnnotation");
		Element software = doc.createElement("SoftwareVersion");
		software.appendChild(doc.createTextNode("Embla"));
		root.appendChild(software);
		Element epoch = doc.createElement("EpochLength");
		epoch.appendChild(doc.createTextNode((String) map[0].get("EpochLength")));
		root.appendChild(epoch);
		Element eventsElmt = doc.createElement("ScoredEvents");
		//System.out.println(map[1].keySet().toString());
		boolean bTranslation = true;
		try{
			BufferedReader input = new BufferedReader(new FileReader(annotation_file));
			String line = "";
			// Recording start time events
			DateFormat df = new SimpleDateFormat("mm/dd/yyyy hh:mm:ss a");
			Date startTime = new Date();
			while ((line=input.readLine())!=null){
				if (line.contains("Date of Recording")){
					String data = line.substring(line.indexOf(":")+2, line.length());
					startTime = df.parse(data);
				} else if (line.contains("Segment Information")){
					String time = input.readLine();
					time = input.readLine();
					//int i = 0;
					
					//Date date = new Date();
					while ((time = input.readLine()).length()>0) {
					String[] data = time.split("\t");
					Date date = df.parse(data[1]);
					long start = (date.getTime() - startTime.getTime())/1000;
					String[] elmts = new String[3];
					elmts[0]="Recording Start Time";
					elmts[1]=Long.toString(start);
					elmts[2]=data[4];
					Element clockElmt = doc.createElement("ClockTime");
					clockElmt.appendChild(doc.createTextNode(data[1]));
					Element elmt = addElements(doc,elmts);
					date = df.parse(data[1]);
					
					elmt.appendChild(clockElmt);
					eventsElmt.appendChild(elmt);
					} 
				} else if (line.contains("Adult Stages")) {
					int num;
					String[] d = line.split("\\(");
					d = d[1].split(" ");
					num = Integer.parseInt(d[0]);
					line = input.readLine();
					line = input.readLine();
					line = input.readLine();
					//System.out.println(line);
					String[] data = line.split(",");
					if (data.length==7){
						if(map[2].keySet().contains(data[6].trim())){
						String stage = data[6].trim();
						int count = 1;
						String[] elmts = new String[3];
						elmts[0] = (String) map[2].get(stage);
						elmts[1] = data[3].trim();
						
						//int i =0;
						
						for (int i=1;i<num; i++){
							//System.out.println(line.length());
							line = input.readLine();
							data = line.split(",");
							if (data[6].trim().equals(stage)){
							count ++;
							} else {
								float duration = (float) (count * 30.0);
								elmts[2] = Float.toString(duration);
								Element elmt = addElements(doc,elmts);
								eventsElmt.appendChild(elmt);
								
								stage = data[6].trim();
								count = 1;
								elmts[0] = (String) map[2].get(stage);
								elmts[1] = data[3].trim();
								
							}
							//j++;
						}
						
						float duration = (float) (count * 30.0);
						elmts[2] = Float.toString(duration);
						Element elmt = addElements(doc,elmts);
						eventsElmt.appendChild(elmt);
						
					}
					}
				} else if (!line.contains("Desaturation")) {
					int j = 0;
					String[] data = line.split(",");
					if (data.length==7){
						//System.out.println(data[6]);
						if (map[1].keySet().contains(data[6].trim())){
							//System.out.println(line);
							String[] elmts = new String[3];
							elmts[0] = ((ArrayList<String>) map[1].get(data[6].trim())).get(1);
							elmts[1] = data[3].trim();
							elmts[2] = data[4].trim();
							Element elmt = this.addElements(doc, elmts);
							eventsElmt.appendChild(elmt);
							
							//sleep stages
						} else {
							String str = annotation_file + "," + data[3].trim() + ", " + data[6].trim();
							log(str);
						}
					}
				} else if (line.contains("Desaturation")){
					String[] d = line.split("\\(");
					d = d[1].split(" ");
					int num = Integer.parseInt(d[0]);
					//System.out.println(num);
					String[][] elmts = new String[num][5];
					line = input.readLine();
					line = input.readLine();
					for (int i = 0; i< num; i++){
						
						line = input.readLine();
						//System.out.println(line);
						String[] data = line.split(",");
						elmts[i][0]="SDO:HemoglobinOxygenDesaturationFinding";
						elmts[i][1]=data[3].trim();
						elmts[i][2] = data[4].trim();
						elmts[i][3] = data[6].trim();
					}
					line = input.readLine();
					line = input.readLine();
					line = input.readLine();
					line = input.readLine();
					for (int i =0; i<num; i++){
						line = input.readLine();
					}
					line = input.readLine();
					line = input.readLine();
					line = input.readLine();
					line = input.readLine();
					for (int i = 0; i<num; i++){
						line = input.readLine();
						String[] data = line.split(",");
						elmts[i][4] = data[6].trim();
					}
					
					for (int i=0; i<num; i++){
						Element elmt = addElements(doc,elmts[i]);
						Element nadir = doc.createElement("SpO2Nadir");
						nadir.appendChild(doc.createTextNode(elmts[i][3]));
						elmt.appendChild(nadir);
						Element baseline = doc.createElement("SpO2Baseline");
						baseline.appendChild(doc.createTextNode(elmts[i][4]));
						elmt.appendChild(baseline);
						eventsElmt.appendChild(elmt);
					}
				} 
			} 
			root.appendChild(eventsElmt);
		} catch (Exception e){
			e.printStackTrace();
			bTranslation = false;
			
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log(errors.toString());
		}
		doc.appendChild(root);
		saveXML(doc, output_file);
		return bTranslation;
	}

	public boolean convertCSV(String annotation_file, String stage_file, String edf_file, String mapping_file, String output_file){
		HashMap[] map = readMapFile(mapping_file);
		Document doc = new DocumentImpl();
		Element root = doc.createElement("PSGAnnotaion");
		Element software = doc.createElement("SoftwareVersion");
		software.appendChild(doc.createTextNode("Respironics"));
		root.appendChild(software);
		Element epoch = doc.createElement("EpochLength");
		int epochLength = Integer.parseInt((String) map[0].get("EpochLength"));
		epoch.appendChild(doc.createTextNode(Integer.toString(epochLength)));
		root.appendChild(epoch);
		Element events = doc.createElement("ScoredEvents");
		//Recording start time
		String[] times = readEDF(edf_file);
		String[] elmtStr = {"Recording Start Time", "0", times[1]};
		Element eventElmt = addElements(doc,elmtStr);
		Element clockTime = doc.createElement("ClockTime");
		clockTime.appendChild(doc.createTextNode(times[0]));
		eventElmt.appendChild(clockTime);
		events.appendChild(eventElmt);
		//get start time to compute relative start time
		SimpleDateFormat df = new SimpleDateFormat("dd.mm.yy hh.mm.ss");
		Date clocktime = new Date();
		try {
			clocktime = df.parse(times[0]);
		}catch (Exception e){
			e.printStackTrace();
			
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log(errors.toString());
		}
		df.applyPattern("hh:mm:ss a mm/dd/yyyy");
		
		boolean bTranslation = true;
		try {
			//get events
			File file = new File(annotation_file);
			Scanner s = new Scanner(file);
			String line = s.nextLine();
			while (s.hasNext()){
				line = s.nextLine();
				//.out.println(line);
				String[] data = line.split("\",");
				Date time = df.parse(data[2].substring(1) + " "+data[4].substring(1));
				String[] elmts = new String[3];
				//System.out.println(map[1].keySet().toString());

				if (map[1].keySet().contains(data[0].substring(1))){
					//System.out.println("here");
					elmts[0] = ((ArrayList<String>) map[1].get(data[0].substring(1))).get(1);
					long starttime = (time.getTime() - clocktime.getTime())/1000;
					elmts[1]= Long.toString(starttime);
					elmts[2] = data[5].substring(1);
					Element event = addElements(doc,elmts);
					if  (((ArrayList<String>) map[1].get(data[0].substring(1))).get(0).contains("Desaturation")){
						Element nadir = doc.createElement("SpO2Nadir");
						nadir.appendChild(doc.createTextNode(data[10].substring(1)));
						event.appendChild(nadir);
						Element baseline = doc.createElement("SpO2Baseline");
						baseline.appendChild(doc.createTextNode(data[9].substring(1)));
						event.appendChild(baseline);
					}
					events.appendChild(event);
				} else {
					elmts[0] = "Technical Note Event";
					long starttime = (time.getTime() - clocktime.getTime())/1000;
					elmts[1]= Long.toString(starttime);
					elmts[2] = data[5].substring(1);
					Element event = addElements(doc,elmts);
					Element note = doc.createElement("Notes");
					note.appendChild(doc.createTextNode(data[0].substring(1)));
					event.appendChild(note);
					events.appendChild(event);
					String log = annotation_file + "," + data[0].substring(1) + ", " + data[2].substring(1) + ", " + data[4].substring(1);
					log(log);
	
				}
			}
			// get stages
			s = new Scanner(new File(stage_file));
			line = s.nextLine();
			line = s.nextLine();
			String[] sleep = line.split(",");
			String stage ="";
			int count = 0;
			int start = 0;
			if (map[2].keySet().contains(sleep[0])){
				stage = sleep[0];
				count = 1;
			
			}
			while (s.hasNext()){
				line = s.nextLine();
				
				sleep = line.split(",");
				if (sleep[0].equalsIgnoreCase(stage)){
					count = count +1;
				} else {
					String[] elmts = new String[3];
					elmts[0] =(String) map[2].get(stage);
					elmts[1]=Integer.toString(start);
					elmts[2]= Long.toString(Integer.parseInt((String) map[0].get("EpochLength")) * count);
					Element event = addElements(doc,elmts);
					events.appendChild(event);
					
					start = start + Integer.parseInt((String) map[0].get("EpochLength"))* count;
					count = 1;
					stage = sleep[0];
				}
			}
			String[] elmts = new String[3];
			elmts[0] =(String) map[2].get(stage);
			elmts[1]=Integer.toString(start);
			elmts[2]= Long.toString(Integer.parseInt((String) map[0].get("EpochLength")) * count);
			Element event = addElements(doc,elmts);
			events.appendChild(event);
		} catch (Exception e){
			e.printStackTrace();
			bTranslation = false;
			
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log(errors.toString());
		}
	
		root.appendChild(events);
		doc.appendChild(root);
		saveXML(doc, output_file);
		return bTranslation;
	}

	public boolean convertSandman(String annotation_file, String edf_file, String mapping_file, String output_file){
		HashMap[] map = readMapFile(mapping_file);
		Document doc = new DocumentImpl();
		Element root = doc.createElement("PSGAnnotation");
		Element software = doc.createElement("SoftwareVersion");
		software.appendChild(doc.createTextNode("Sandman"));
		Element epoch = doc.createElement("EpochLength");
		epoch.appendChild(doc.createTextNode((String) map[0].get("EpochLength")) ) ;
		root.appendChild(software);
		root.appendChild(epoch);
		String efile = edf_file;
		Element eventsElmt = doc.createElement("ScoredEvents");
		String[] elmtsStr = new String[3];
		String[] timeStr = readEDF(efile);
		elmtsStr[0] = "Recording Start Time";
		elmtsStr[1] = "0";
		elmtsStr[2] = timeStr[1];
		Element eventElmt = addElements(doc,elmtsStr);
		Element startTimeElmt = doc.createElement("ClockTime");
		startTimeElmt.appendChild(doc.createTextNode(timeStr[0]));
		eventElmt.appendChild(startTimeElmt);
		eventsElmt.appendChild(eventElmt);
		SimpleDateFormat df = new SimpleDateFormat("dd.mm.yyyy hh.mm.ss");
		Date clocktime = new Date();
		try {
			clocktime = df.parse(timeStr[0]);
		}catch (Exception e){
			e.printStackTrace();
			
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log(errors.toString());
		}
		df.applyPattern("dd.mm.yyyy hh:mm:ss a");
		String date = timeStr[0].split(" ")[0];
		//String date = time[0].split(" ")[0];
		//String[] timeStr = time[0].split(" ")[1].split(".");
		//clocktime = df.parse(date + " " + timeStr[0])
		boolean bTranslation = true;
		try {
			File file = new File(annotation_file);
			BufferedReader input = new BufferedReader(new FileReader(annotation_file));
			
			String line = input.readLine();
			while (!line.contains("Epoch")){
				line = input.readLine();
			}
			while ((line = input.readLine())!=null){
				String[] data = line.split("\t");
				String eventname = data[1];
				//Element event = doc.createElement("ScoredEvent");
				String[] elmts = new String[3];
				
				//scored events
				if (map[1].keySet().contains(data[1])){
					
					elmts[0] = ((ArrayList<String>) map[1].get(data[1])).get(1);
					Date starttime = df.parse(date+ " " + data[2]);
					long time = (starttime.getTime() - clocktime.getTime())/1000;
					if (time < 0){
						time += 24*60*60;
					}
					elmts[1]=Long.toString(time);
					//startElmt.appendChild(doc.createTextNode(Long.toString(time)));
					if (data.length>3){
						//System.out.println(data[1]);
						//durationElmt.appendChild(doc.createTextNode(data[3]));
						elmts[2]=data[3];
					}
					else {
						elmts[2] = Integer.toString(0);
					}
					Element event = addElements(doc,elmts);
					if (((ArrayList<String>) map[1].get(data[1])).size()>2){
						Element notesElmt = doc.createElement("Notes");
						notesElmt.appendChild(doc.createTextNode(((ArrayList<String>) map[1].get(data[1])).get(2)));
						event.appendChild(notesElmt);
					}
					
					if (((ArrayList<String>) map[1].get(data[1])).get(1).compareToIgnoreCase("Desaturation")!=0){
						
					}
					eventsElmt.appendChild(event);
				} else if (!map[2].keySet().contains(data[1])) {

					
					Element notesNode = doc.createElement("Notes");
					elmts[0]="Technician Notes";
					//nameElmt.appendChild(doc.createTextNode("Technician Notes"));
					Date starttime = df.parse(date+ " " + data[2]);
					long time = (starttime.getTime() - clocktime.getTime())/1000;
					if (time < 0){
						time += 24*60*60;
					}
					elmts[1] = Long.toString(time);
					//startElmt.appendChild(doc.createTextNode(Long.toString(time)));
					if (data.length>3){
						elmts[2] = data[3];
						//durationElmt.appendChild(doc.createTextNode(data[3]));
					} else {
						elmts[2] = Integer.toString(0);
					}
					notesNode.appendChild(doc.createTextNode(eventname));
					
					
					Element event = addElements(doc,elmts);
					event.appendChild(notesNode);
					
					String info = annotation_file + "," + eventname + "," + data[2];// + Double.toString(starttime);
					this.log(info);
					eventsElmt.appendChild(event);
				}
				
			}
			
			//sleep staging
			input = new BufferedReader(new FileReader(annotation_file));
			line = input.readLine();
			while (!line.contains("Epoch")){
				line = input.readLine();
			}
			while((line=input.readLine())!=null){
				String[] data = line.split("\t");
				String stage ="";
				if (map[2].keySet().contains(data[1])){
					if (data[1].compareToIgnoreCase(stage)!=0 & data.length>=4) {
						String[] elmts = new String[3];
						elmts[0] = (String) map[2].get(data[1]);
						Date starttime = df.parse(date+ " " + data[2]);
						long time = (starttime.getTime() - clocktime.getTime())/1000;
						if (time < 0){
							time += 24*60*60;
						}
						elmts[1] = Long.toString(time);
						elmts[2] = data[3];
						Element event = addElements(doc,elmts);
						eventsElmt.appendChild(event);
						stage = data[1];
					}
				}
			}
			
		} catch(Exception e){
			e.printStackTrace();
			bTranslation = false;
			
			StringWriter errors = new StringWriter();
			e.printStackTrace(new PrintWriter(errors));
			log(errors.toString());
		}
		root.appendChild(eventsElmt);
		doc.appendChild(root);
		saveXML(doc, output_file);
		return bTranslation;
	}
	
	// log file for events not in mapping: input file name, event, starttime,
	// output as tech notes event in xml files with notes field is the actual event name.

}