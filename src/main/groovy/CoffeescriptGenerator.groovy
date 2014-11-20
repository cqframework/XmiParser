import groovy.transform.TypeChecked
import groovy.util.slurpersupport.Attribute
import groovy.util.slurpersupport.Attributes
import groovy.xml.MarkupBuilder

/**
 Uses Parsed XMI to generate "javadoc"-like HTML output
 @author Jason Mathews, MITRE Corporation
 Created on 10/8/2014.
 -----------------------------------------------------------------------------------
 Copyright (C) 2014 The MITRE Corporation. All Rights Reserved.
 The program is provided "as is" without any warranty express or implied, including
 the warranty of non-infringement and the implied warranties of merchantability and
 fitness for a particular purpose.  The Copyright owner will not be liable for any
 damages suffered by you as a result of using the Program.  In no event will the
 Copyright owner be liable for any special, indirect or consequential damages or
 lost profits even if the Copyright owner has been advised of the possibility of
 their occurrence.
 */
class CoffeescriptGenerator extends XmiParser {

	// http://docs.oracle.com/javase/7/docs/api/
	// interfaces, classes, etc in frameset

	static final Set dataTypes = [
		// 'Address, 'TelecomAddress,
		'Code',
		'CodeableConcept',
		'IntervalOfQuantity',
		'Quantity',
		'Text',
		'TimePoint',
		'TimePeriod',
		'YesNo',
		'Value'
	]

	static final Set primitiveTypes = [
		'string',
		'boolean',
		'id',
		'uri',
		'oid',
		'decimal',
		'date',
		'code',
		"date-union",
		'string-primitive',
		'boolean-primitive',
		'id-primitive',
		'uri-primitive',
		'oid-primitive',
		'decimal-primitive',
		'date-primitive',
		'code-primitive',
		'uri-primitive',
		'base64Binary',
		'base64Binary-primitive',
		'dateTime-primitive',
		'dateTime-union',
		'dateTime',
		'integer-primitive',
		"integer",
		"instant",
		"instant-primitive",
		"extension-choice",
		"uuid",
		"uuid-primitive",
		"fhir_Schedule"
	]

	static final Set ignoreTypes = [
		'string',
		'boolean',
		'id',
		'uri',
		'oid',
		'decimal',
		'date',
		'code',
		"date-union",
		'string-primitive',
		'boolean-primitive',
		'id-primitive',
		'uri-primitive',
		'oid-primitive',
		'decimal-primitive',
		'date-primitive',
		'code-primitive',
		'uri-primitive',
		'base64Binary',
		'base64Binary-primitive',
		'dateTime-primitive',
		'dateTime-union',
		'dateTime',
		'integer-primitive',
		"integer",
		"instant",
		"instant-primitive",
		"extension-choice",
		"uuid",
		"uuid-primitive",
		"fhir_Schedule"
	]

	// NOTE: can make propTypes a localField in createDetailPages() and pass as arg to dumpAttributes()
	final def propTypes = [:]

	final Set<String> types = new TreeSet<>()

	final Set<IndexInfo> index = new TreeSet<>()

	final Set<String> interfaceSet = new HashSet<>()

	private boolean flatListMode

	private PrintWriter outWriter

	// temp fields set in dumpAttributes
	private String lastParent
	private boolean multiParentAttributes
	private String summaryDescription
	private Set models = []
	// -----------------------------------------------

	CoffeescriptGenerator(File file) {
		super(file)
		interfaceSet.addAll(interfaces)
	}


	// -----------------------------------------------

	void init() {
		super.init()
	}

	// -----------------------------------------------

	void output() {
		if (!elements) {
			println "ERROR: failed to find any elements"
			return
		}

		File pageDir = new File('coffeescript/quick')
		if (!pageDir.exists()) pageDir.mkdirs()
		outWriter = new PrintWriter(new FileWriter(file.getName() + "-details.txt")) // debug
		createDetailPages()

		if (outWriter) {
			outWriter.close() // debug
		}
		writeModelIndex()
	}




	// ---------------------------------------------------------
	// create class detail pages
	// ---------------------------------------------------------
	@TypeChecked
	void createDetailPages() {
		// println elements.keySet()
		// [ActionNonPerformance, ActionPerformance, AdverseEvent, AllergyIntolerance, ... VaccinationProtocol, Vaccine]
		// println elements
		String packageName

		// for each class and interface in the model
		elements.each { String name, elt ->
			if (name.startsWith('package:')) {
				if (packages.size() > 3) {
					packageName = name.substring(8)
				}
				// if few package then skip package names
				return
			}
			// skip package names
			if (elt != null) {
				lastParent = null
				multiParentAttributes = false
				//dumpDebugMode = false // debug
				createDetailPage(packageName, name, elt, false)
				//printf "ABC: %s %b%n", name, multiParentAttributes
				//if (name == 'EncounterPerformanceOccurrence') println "=" * 65
			}
		} // each element
	} // createDetailPages()

  void writeModelIndex(){
  	def includeWriter = new WrappedWriter(new FileWriter("coffeescript/quick/models.coffee"))
  	includeWriter.puts("this.QUICK || {}")
  	models.each {name ->
  		includeWriter.puts("QUICK.$name = require './$name'")
  	}
  	includeWriter.puts("module.exports.QUICK = QUICK")
  	includeWriter.close()
  }
	// ---------------------------------------------------------
	// create class detail page
	// ---------------------------------------------------------
	void createDetailPage(String packageName, String name, elt, boolean flatList) {

		String type = elt.'@xmi:type'.text()
		boolean isInterface = type == 'uml:Interface'
		if (isInterface || ignoreTypes.contains(name) || name.startsWith('fhir:')) {
			return
		}

		Set requires = []
		String nameOut = escapeName(name) // e.g. for RIM with <'s in names
		flatListMode = flatList

		def writer = new WrappedWriter(new FileWriter("coffeescript/quick/${nameOut}.coffee"))
		models.add(name)

		// <packagedElement xmi:type="uml:Class"
		// <packagedElement xmi:type="uml:Interface"

		//Parent class: <Parent class>
		Set<String> parentNames = parents.get(name)

		def classesList
		def interfaceList

		def refs = connectors.connector.findAll {
			it.target.model.@name.text() == name // || !isInterface && it.source.model.@name.text() == name
		}
		// e.g. class Statement superclass has StatementAboutObservation subclass connection
		if (! refs.isEmpty()) {
			interfaceList = []
			classesList = []
			refs.each { connection ->
				def srcModel = connection.source.model
				if (srcModel) {
					def srcName = srcModel.@name.text()
					if (srcName) {
						String srcType = srcModel.@type.text()
						String linkType = connection.properties.@ea_type.text()
						if (srcType == 'Interface') {
							if (linkType == 'Generalization')
								// src=FamilyHistoryDescriptor[interface] target=ObservableDescriptor[interface] type=Generalization
								interfaceList.add(srcName)
							// else println "A-other: $srcName type=$srcType [$linkType]" // e.g other: ConditionDetail type=Interface [Aggregation]
						}
						else if (srcType == 'Class' && (linkType == 'Generalization' || linkType == 'Realisation')) {
							// src=EncounterEvent[class] target=EncounterDescriptor[interface] type=Realisation
							classesList.add(srcName)
							//println "X: $name $srcName $linkType"
							// e.g. StatementAboutObservation[class] <= PhenomenonPresence[class] [Generalization]
							//      ProposalFor[interface] <= ProgramParticipationProposal[class] [Realisation]
						}
						// else println "B-other: $srcName type=$srcType [$linkType]"
						// e.g. ConditionPresent type=Class [Aggregation
					}
				}
			} // each connector reference
		} // refs.isEmpty()

		writeHeader(writer)
		String desc = propDesc[name]

		writer.ln("###*");
		if (desc){
			writer.ln(desc)
			writer.ln(" ")
		}

		writer.ln("###")

    def bodyWriter = new WrappedWriter(new StringWriter())
    bodyWriter.ln("###*");
		bodyWriter.ln("@class $name");
		bodyWriter.ln("@exports  $name as $name");
		bodyWriter.ln("###");
		bodyWriter.puts("class $name")
		bodyWriter.sb("constructor: (@json) ->")
		//bodyWriter.sb("super()")
		bodyWriter.eb(" ")
		bodyWriter.eb("")

		Collection namedAttrs = new ArrayList()
		Set subinterfaces = new TreeSet()


		// Implemented interfaces: <Also from XMI>
		// TODO: if class implements multiple interface this won't find those
		// CommunicationOrder <= ActionDescriptor <= CommunicationDescriptor
		// CommunicationOrder <= [ActionDescriptor, CommunicationDescriptor]
		def ref = interfaceMap[name]
		if (ref) {
			//subinterfaces = new ArrayList<String>()
			// All Known Subinterfaces: e.g. ObjectInput extends DataInput interface
			// DataInput subinterface: ObjectInput
			while (ref) {
				subinterfaces.add(ref)
				ref = interfaceMap[ref]
			}
		} // if ref

		// ActionDescriptor, CommunicationDescriptor, Order interfaces
		// if (name=='CommunicationOrder') println subinterfaces // debug


		// class
		refs = connectors.connector.findAll {
			it.source.model.@name == name && it.target.model.@type == 'Interface' && it.properties.@ea_type == 'Realisation'
			// ignores Aggregation and Association connections
		}

		/*
		 EncounterEvent: ActionPerformance Class
		 EncounterEvent: Condition Class
		 EncounterEvent: Condition Class
		 EncounterEvent: Condition Class
		 EncounterEvent: EncounterDescriptor Interface
		 EncounterEvent: Performance Interface
		 EncounterRequest > interface Order
		 etc.
		 */
		if (!refs.isEmpty()) {
			refs.each {
				// if (it.properties.@ea_type != 'Generalization') return// skip Aggregations + Associations
				def tgtName = it.target.model.@name.text()
				// recursively add interfaces and their super-interfaces
				// e.g. CommunicationOrder <= Order <= ActionPhase
				while (tgtName && subinterfaces.add(tgtName)) {
					tgtName = interfaceMap[tgtName]
				}
			}
		} // if refs

		// <Description from XMI>
		// def attrs = elt.ownedAttribute
		// NOTE: namedAttrs map is empty at this point
		dumpAttributes(elt, namedAttrs, null)
		// if (validate && namedAttrs.isEmpty() && !subinterfaces && elt.@isAbstract != 'true') println "XXX: $name base class has no properties" // debug validation

		// recursively add attributes from parents
		if (parentNames) {
			Set<String> visitedClasses = new HashSet<>()
			parentNames.each { String parentName ->
				def parent = elements.get(parentName)
				if (parent && visitedClasses.add(parentName)) {
					// println "\t$parentName"
					dumpAttributes(parent, namedAttrs, name)
					// allow for multiple inheritance
					checkParents(parentName, namedAttrs, name, visitedClasses)
				}
			}
		}

		// add attributes and aggregate connections from interfaces
		if (subinterfaces) {
			subinterfaces.each {
				def parent = elements.get(it)
				dumpAttributes(parent, namedAttrs, name)
			}			//subinterfaces.clear()
		}



		if (namedAttrs) {
			def sortedAttrMap = new TreeMap<String, String>()
			def sortedAttrsSet = new TreeSet()
			String parentName
			namedAttrs.each { attr ->
				if (attr instanceof groovy.util.slurpersupport.Attributes) {
					parentName = attr.text()
					//println "XX: $parentName"
				} else {
					String oldValue = sortedAttrMap.put(attr, parentName)
					if (outWriter && oldValue != null && oldValue != parentName) outWriter.println "\tERROR: attribute conflict: $attr $oldValue $parentName" // debug
					if (!sortedAttrsSet.add(attr) && validate) println "WARN: duplicate attribute $attr in $name"
					//println "\t$attr"
				}
			}
			namedAttrs = sortedAttrsSet
			println namedAttrs
			namedAttrs.each { attr ->
				if (attr instanceof groovy.util.slurpersupport.Attributes) {

					parentName = attr.text()

				}
				else{
					parentName = sortedAttrMap.get(attr)
					// add attribute to sorted set for file output
					// class/interface name
					// attribute - [inherited from class/interface] description

					String propType = propTypes[attr]
					if (propType == null) propType = 'type'


					String outType = propType
					String key = "${parentName}.${attr}"
					String multiplicityValue = multiplicity.get(key) // e.g. [0,*] or [1,*] or [1]
					String pdesc = propDesc[key]
					if(!ignoreTypes.contains(outType) && !(outType == name)){
						requires.add(outType)
					}
					
					writeField(bodyWriter,attr,outType, (multiplicityValue && multiplicityValue != '[1]' && multiplicityValue != '[0,1]' ),pdesc)

				} // foreach namedAttrs
			}

			// namedAttrs + propTypes maps must be cleared for each element

			namedAttrs.clear()
			propTypes.clear()

		}// if namedAttrs
		//for each import add a require statement
		requires.each { req ->
			writer.put("require './$req")
			writer.puts("'")
		}
			
		writer.put(bodyWriter.toString())
		writer.puts("")
		writer.puts("module.exports.$name = $name")
		writer.close()

	} // createDetailPage()


	void writeField(writer,name, type, multi,desc){
		if(desc){
			writer.sb("###*");
			writer.ln(desc)
			writer.ln("### ")
			writer.eb()
		}
		def prim = primitiveTypes.contains(type)
		if(multi ){
			writeMultiField(writer,name,type,prim)
		}
		else{
			writeSingleField(writer,name,type,prim)
		}
	}

	void writeMultiField(writer,name,type,primitive){
		if(primitive){
			writer.sb("$name: ->  @json['$name'] ")
			writer.eb(" ")
			writer.puts(" ")
		}else{
			writer.sb( "$name: -> ")
			writer.sb("if @json['$name']")
			writer.sb("for x in @json['$name'] ")
			writer.sb("new QUICK.$type(x)")
			writer.eb(" ")
			writer.eb()
			writer.eb()
			writer.eb()
		}

	}

	void writeSingleField(writer, name,type,primitive){
		if(primitive)
			writer.sb("$name: ->  @json['$name'] ")
		else
			writer.sb("$name: -> if @json['$name'] then new QUICK.$type( @json['$name'] )")
		writer.eb(" ")
		writer.puts(" ")

	}

	void writeHeader(writer){
		writer.ln("# Copyright (c) 2014 The MITRE Corporation");
		writer.ln("# All rights reserved.");
		writer.ln("# ");
		writer.ln("# Redistribution and use in source and binary forms, with or without modification, ");
		writer.ln("# are permitted provided that the following conditions are met:");
		writer.ln("# ");
		writer.ln("#     * Redistributions of source code must retain the above copyright notice, this ");
		writer.ln("#       list of conditions and the following disclaimer.");
		writer.ln("#     * Redistributions in binary form must reproduce the above copyright notice, ");
		writer.ln("#       this list of conditions and the following disclaimer in the documentation ");
		writer.ln("#       and/or other materials provided with the distribution.");
		writer.ln("#     * Neither the name of HL7 nor the names of its contributors may be used to ");
		writer.ln("#       endorse or promote products derived from this software without specific ");
		writer.ln("#       prior written permission.");
		writer.ln("# ");
		writer.ln("# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS \"AS IS\" AND ");
		writer.ln("# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED ");
		writer.ln("# WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. ");
		writer.ln("# IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, ");
		writer.ln("# INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT ");
		writer.ln("# NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR ");
		writer.ln("# PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, ");
		writer.ln("# WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ");
		writer.ln("# ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE ");
		writer.ln("# POSSIBILITY OF SUCH DAMAGE.");
		writer.ln("###*");
		writer.ln("@namespacing scoping into the QUICK namespace");
		writer.ln("###");
		writer.ln("this.QUICK ||= {}");
	}

	// ---------------------------------------------------------

	@TypeChecked
	void checkParents(String parentName, Collection namedAttrs, String name, Set visitedClasses) {
		Set<String> parentNames = parents.get(parentName)
		parentNames.each { String pName ->
			def parent = elements.get(pName)
			if (parent && visitedClasses.add(pName)) {
				// println "\t$pName"
				dumpAttributes(parent, namedAttrs, name)
				checkParents(pName, namedAttrs, name, visitedClasses)
			} // else println "dup/other: $pName"
		}
	}




	// -----------------------------------------------
	void dumpAttributes(elt, Collection namedAttrs, String targetName) {
		int atCnt = 0
		def eltName = elt.@name
		boolean self = targetName == null
		// println eltName.getClass().name // groovy.util.slurpersupport.Attributes
		//println "XXX: elt="+elt.list() // debug
		//String eltType = elt.@'xmi:type'?.text()?.toLowerCase() ?: ''
		//if (eltType.startsWith('uml:')) eltType = eltType.substring(4)
		String eltType = 'uml:Interface' == elt.'@xmi:type'.text() ? 'interface' : 'class'

		// NOTE: when flatListMode is true we're making second pass through same class/element so want to suppress warnings

		// populate namedAttrs + index + types + propTypes fields
		elt.ownedAttribute.each{
			def attrName = it.@name.text()
			if(attrName) {
				if (atCnt++ == 0) namedAttrs.add(eltName) // Attribute

				// check if element needs a flat/non-flat view
				//String parent = self ? 'self' : eltName
				final String name = eltName.text()
				//boolean oldValue = multiParentAttributes
				if (lastParent != null && lastParent != name) multiParentAttributes = true
				/*
				 if(dumpDebugMode && !flatListMode && oldValue != multiParentAttributes) printf "XYZ: %s.%s last=%s %b%n", name, attrName, lastParent, multiParentAttributes
				 if(dumpDebugMode && !flatListMode) {
				 if (oldValue != multiParentAttributes) println "**"
				 printf "XYZ1: %s.%s lastP=%s tgt=%s %b%n", name, attrName, lastParent, targetName, multiParentAttributes
				 }
				 */
				lastParent = name

				//printf "\tattr: %s%n", eltName.list() // debug
				//if (self) index.put(attrName, eltName.text() + ".html")
				if (self) {
					// add top-level attributes to index
					index.add(new IndexInfo(attrName, name + ".html#$attrName",
							"Field in $eltType <a href='pages/${name}.html'>$name</a>"))
					// if (attrName == 'id') println "XX: attr $attrName $eltName $targetName" // debug
				}

				namedAttrs.add(attrName) // String
				//if (attrName == 'protocol' || attrName == 'vaccine') println "XX: attr $attrName $eltName $targetName" // debug
				def typeElt = it.type
				String type = typeElt.'@xmi:idref'.text()
				if (type) {
					if (type.startsWith('EAJava_')) {
						// simple java type (e.g. TimePeriod, Code, Text, etc.)
						type = type.substring(7)
						if (!flatListMode && self && !elements.containsKey(type) && !dataTypes.contains(type)) {
							//printf '5: primtype %-25s %-14s\t%s%n', eltName, attrName, type
							printf '5: primtype %s.%s %s%n', eltName, attrName, type
						}
					} else if (type.startsWith('EAID_')) {
						def oldType = type
						type = classId[type]
						/*
						 <ownedAttribute xmi:type="uml:Property" xmi:id="EAID_dst9CDB8F_38AD_4257_B450_6A4970162FE3" name="contraindicatedMedication"
						 visibility="public" association="EAID_D39CDB8F_38AD_4257_B450_6A4970162FE3" isStatic="false" isReadOnly="false"
						 isDerived="false" isOrdered="false" isUnique="true" isDerivedUnion="false" aggregation="none">
						 <type xmi:idref="EAID_4C9444BB_0BA4_4bb6_83C2_572602B3567C"/>
						 </ownedAttribute>
						 */
						if (!type) {
							// xmi:type="uml:Property">
							type = 'ComplexType'
							if (!flatListMode) printf 'WARN: unknown type: %s/%s type=%s%n', elt.@name, attrName, oldType // , it.'@xmi:type'
							// QUICK: WARN: unknown type: extension-choice/valueSchedule type=EAID_3193F607_BC5E_401f_8BC4_B8C5C2EB3211
							//XXX: unknown type: ContraindicationToMedication/contraindicatedMedication type=EAID_4C9444BB_0BA4_4bb6_83C2_572602B3567C
							//XXX: unknown type: ContraindicationToProcedure/contraindicatedProcedure type=EAID_3CE9C7BA_A6E5_40d5_B30F_694AFE3E8FE9
							//XXX: unknown type: ResultGroup/component type=EAID_1C9030A6_1EFB_4bb6_990D_AA196EB4972F
						}
					} // EAID mapping
					propTypes[attrName] = type
					types.add(type)

				} else {
					type = typeElt.'@xmi:type'

					if (type == 'uml:PrimitiveType') {
						// href="http://schema.omg.org/spec/UML/2.1/uml.xml#String"/>
						type = typeElt.@href.text()
						if (type) {
							int ind = type.indexOf('#')
							if (ind > 0) {
								type = type.substring(ind+1)
								// Boolean, Integer, String
								// println "XXX: type=$type"
							} else {
								if (!flatListMode) println "WARN: unknown primitive type: $attrName $type"
								return
							}
						}
					} else if (type == 'uml:Enumeration') {
						type = 'Code'
					} else {
						if (!type) {
							type = it.'@type' // e.g. RIM
							// <ownedAttribute xmi:type="uml:Property" xmi:id="_3883B5D002C03DEC1E280271" name="negationInd" visibility="public" type="_uXB_cnB_EeCmhtN3-wNrqw">
							// <packagedElement xmi:type="uml:PrimitiveType" xmi:id="_uXB_cnB_EeCmhtN3-wNrqw" name="BL"/>
							if (type) {
								// TODO:  add type/id lookup
								def typeName = classId.get(type)
								//println "$type => $typeName"//debug
								if (typeName) type = typeName
								else if (type.startsWith("_")) type = ''
							}
						}
						if (!type) {
							if (!flatListMode) println "WARN: unknown type: $attrName $type"
							return
						}
					}
					if (type) {
						propTypes[attrName] = type
						types.add(type)
					}
				}
				//if (type == 'Period') printf "XX: %s %s [%s] %s%n", eltName, attrName, type, targetName //debug
			}
			// else println "\tUNK" // e.g. Vaccine [last attribute]
			/*
			 <ownedAttribute xmi:type="uml:Property" xmi:id="EAID_dst24D506_B03B_46c3_A08F_7947AD5BCAE5"
			 visibility="public" association="EAID_E024D506_B03B_46c3_A08F_7947AD5BCAE5"
			 isStatic="false" isReadOnly="false" isDerived="false" isOrdered="false"
			 isUnique="true" isDerivedUnion="false" aggregation="none">
			 <type xmi:idref="EAID_F5BCE8C1_7A0D_42d2_ABD9_F088CA0EDE91"/>
			 </ownedAttribute>
			 */
		} // each attributes
		//return atCnt
		dumpAggregates(elt, namedAttrs, atCnt, eltName.text(), targetName)
	} // end dumpAttributes

	// -----------------------------------------------

	void dumpAggregates(elt, Collection namedAttrs, int atCnt, String name, String targetName) {
		def aggregateList = aggregation.get(name)
		if (aggregateList) {
			// examples:
			//  source ResultDetail -> target ObservationResultDescriptor[detailedResult]
			//  source ProcedureParameters -> target ProcedureDescriptor[details]
			if (atCnt == 0 || namedAttrs.isEmpty()) {
				// first element must be name attribute
				// if (targetName) println "aggregate self $targetName $name" // e.g. EncounterEvent.relatedCondition, Entity.characteristic
				namedAttrs.add(new groovy.util.slurpersupport.Attributes(
						new Attribute('name', name, elt, '', EMPTY_MAP), 'ownedAttribute', EMPTY_MAP))
				// println "\tempty attrs"
			}
			aggregateList.each { Aggregate aggregate ->
				// attribute name = role, type = source
				if (validate && !flatListMode && namedAttrs.contains(aggregate.role)) {
					printf('ERROR: role %s conflicts with attribute of same name in %s%n', aggregate.role, name)
				}
				namedAttrs.add(aggregate.role)
				//String label
				String key = "${name}.${aggregate.role}"
				if (aggregate.multiplicity) {
					multiplicity[key] = "[" + aggregate.multiplicity + "]"
				}
				if (aggregate.label) {
					//label = "XXX:"+aggregate.label
					propDesc[key] = aggregate.label
				}
				//else label = 'Aggregate property'
				//propDesc["${name}.${aggregate.role}"] = label
				propTypes[aggregate.role] = aggregate.source
			}
		}
	}

	// -----------------------------------------------

	// utility methods
	@TypeChecked
	static void copyFile(String sourceName, String targetName) {
		File target = new File(targetName)
		if (target.exists()) return
			File source = new File(sourceName)
		if (!source.exists()) throw new FileNotFoundException(source.toString())
		target.setBytes(source.getBytes())
		target.setLastModified(source.lastModified())
	}

	@TypeChecked
	static String escapeName(String s) {
		// invalid characters for windows filenames see http://support.microsoft.com/kb/177506
		s.replaceAll('[:<>?*/|]', '_')
	}





	static class WrappedWriter {
		java.io.Writer writer;
		int indent = 0
		int indentSize = 2
		String currentIndent = ""
		WrappedWriter(writer){
			this.writer = writer
		}

		void ln(String string){
			puts(string)
		}
		void puts(String string){
			put(string)
			writer.write("\n")
		}

		void put(String string){
			writer.write(currentIndent.toCharArray())
			writer.write(string.toCharArray())
		}

		void sb(String str){
			indentPlus()
			if(str){
				puts(str)
			}
		}

		void eb(String str){
			indentMinus()
			if(str){
				puts(str)
			}
		}

		void indentPlus(){
			indent+= indentSize
			setIndentString()
		}


		void indentMinus(){
			indent-= indentSize
			if(indent < 0){
				indent=0
			}
			setIndentString()
		}

		void setIndentString(){
			String str = ""
			for(int i=0;i< indent;i++){
				str += " "
			}
			currentIndent = str
		}
		void close(){
			writer.flush();
			writer.close()
		}

		String toString(){
			writer.toString()
		}
	}

	// -----------------------------------------------

	@TypeChecked
	static class IndexInfo implements Comparable<IndexInfo> {
		String name
		String href
		String desc

		IndexInfo(name, href, desc) {
			if (!name) throw new IllegalArgumentException()
			this.name = name
			if (!href) throw new IllegalArgumentException()
			this.href = href
			if (desc == null) desc = ''
			this.desc = desc
		}

		/**
		 * Compare one IndexInfo to another. First compare the
		 * names and if names are equal then compare the descriptions.
		 * @param   o the IndexInfo to be compared.
		 */
		@Override
		int compareTo(IndexInfo o) {
			int cmp = name.compareToIgnoreCase(o.name)
			return cmp != 0 ? cmp : desc.compareToIgnoreCase(o.desc)
		}
	}

}
