import groovy.transform.TypeChecked

// $Id: parse.groovy,v 1.24 2014/09/22 10:59:42 mathews Exp mathews $
// -----------------------------------------------
/**

Parse XMI XML input file and create simple structures to enable subclasses
to quickly validate or generate HTML from the model.

QIDAM EA XMI structure:

<?xml version="1.0" encoding="windows-1252"?>
<xmi:XMI xmi:version="2.1"
		xmlns:thecustomprofile="http://www.sparxsystems.com/profiles/thecustomprofile/1.0"
		xmlns:uml="http://schema.omg.org/spec/UML/2.1" xmlns:xmi="http://schema.omg.org/spec/XMI/2.1">

	<uml:Model xmi:type="uml:Model" name="EA_Model" visibility="public">
			<packagedElement isAbstract="true" name="Statement"
			...
	</uml:Model>
	<xmi:Extension extender="Enterprise Architect" extenderID="6.5">
		<elements>
		</elements>
		<connectors>
          ...
		</connectors>
		<primitivetypes>
          ...
		</primitivetypes>
		<profiles>
          ...
		</profiles>
		<diagrams>
          ...
		</diagrams>
	</xmi:Extension>
</xmi:XMI>

 Changes:
 - Added list of Direct Known Subclasses for classes
 - Added Frames/No Frames navigation links to details page header
 - Added class, interface, and attribute descriptions on details page
 - Added type on the `detailed pages.
 - Populate overview summary page with class/interface list with descriptions for each
 - Added index page with sorted list of attributes
 - Refactored large main script into top-level class with functions
 - Add link to Field Types where Class/Interface to appropriate target page (e.g. Class UndeliveredProcedure/@actionParticipant type=Participant)
 - Mark when classes are abstract (e.g. Statement) see http://docs.oracle.com/javase/7/docs/api/javax/swing/AbstractAction.html
 - Find hasA/containedWithin links (e.g. Schedule + BodySite classes used as type of Attribute property used by other classes/interfaces)
 - Added check to exclude references listed in ownedAttribute under packagedElements which are not true attributes or references in the
   way BodySite is referenced on a attribute in the ConditionDescriptor interface.
 - Support Aggregation connections (e.g. add "details" as attribute/association in ProcedureDescriptor interface)
   with multiplicity relationship; e.g. 0..* on descriptions
 - Handle multiple packageElement packages contained under parent model package in XMI such as in VMR
 - Use italics for interface names in Fields inherited from XXX in detailed pages
 - Add basic data types (e.g. TimePoint) descriptions to overview page summary
   and link to those from the detailed pages.
 - Add multiplicity on properties to descriptions; e.g., GoalDescriptor.associatedCarePlan[0..*]
 - Move aggregation association multiplicity label to Type suffix rather than appended to description
 - Fixed listing interface aggregation and associations as implementing interface
   need explicit test for Realisation or Generalization link types.
 - Use descriptions from Associations correctly.
 - Perform depth-first scan of packages to reach all packages regardless of the structure (needed for rim.xmi)
 - Add attributes from aggregate connections via interfaces (e.g. ProcedureOrder <= ProcedureDescriptor.details)
 - Add aggregation/association properties to index list output
 - Add package names to TOC class listing
 - Add option to flatten hierarchy wrt to inherited attributes by class/interface in detailed page and show all attributes in
   single list sorted by name.
 - Support for classes with multiple class inheritance.
 - Simplified multiple class inheritance and parent lookup
 - Split left-pane of index with package-list top and package class list on bottom if package count > 3
 - Fix link to overview + detail html pages with special characters in the name (e.g. QSET<TS>)
 - Add auto-descriptions for QLIM base leaf nodes (e.g. ProcedureOrder => xxx)
 - Add check for potential abstract mismatches between sibling elements having same base parent
 - Allow QIDAM Class Model to have sub-packages (e.g. action/act, common/entity) and make package
   name the aggregate of parent package names (added packageCtx field)
 - Customize new auto-generation of leaf-level nodes for QIDAM-transformed model
 - Minor tweaks for QUICK data model
 - Add package name to detailed pages
 - Sort list of Direct Known Subclasses
 - Format Occurrence detail pages adding <pre> block around examples in QUICK
 - Add hyperlinks to URLs in detailed page attribute descriptions.
 - Remove flat option and auto-generate flat pages when needed with links to non-flat detailed page

 Notes:
 - Some classes in QIDAM have no properties (e.g. BodySite, Location). These are placeholders
   to be defined in logical model.

 TODO:
 - Add link from detail page (e.g. AdverseEvent to AdverseEvent_NonOccurrence)
 - Catch indirect composite/aggregation associations via interfaces;
   e.g. ConditionDescriptor interface has conditionDetail [0..*] reference to ConditionDetail interface.
 - NutritionDescriptor interface has nutritionItem [0..*] reference --appears in interface not class detail.
 - If multiple packages have same element name then we overwrite other reference. Must be unique within the package not globally.
 - Generalize the table of data types on the first page, externalize the data types, etc.
 - If model package count > 3 then use custom index.html template (like actual javadoc one)
   with separate package panel and package class panel with new all classes list (sorted by name) and package list pages.
 - For QUICK need to strip out Example: snippet to make for a short readable class summary in overview-summary.html

 @author Jason Mathews, MITRE Corporation

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
class XmiParser {

/*
attribute types:

        [EAID_XXX = id mapping to classes/interfaces]
        EAID_0658A714_BCB0_49e0_BE41_106F5415E52E [Condition]
        ...
        EAID_FB39C456_57D0_4780_8F6A_8199ECEB8D2F [Organization]

        ActionPerformance
        AdministerableSubstance
        BodySite
        Code
        Condition
        EncounterEvent
        Entity
        ImmunizationRecommendation
        IntervalOfQuantity
        Location
        Medication
        MedicationAdministrationDescriptor
        ObservationResultDescriptor
        Organization
        Participant
        Patient
        PersonRole
        ProcedureDescriptor
        Quantity
        Schedule
        Statement
        Substance
        Text
        TimePeriod
        TimePerioid (typo)
        TimePoint
        Value
        YesNo
 */

  final Set<String> types = new TreeSet<>()

  static boolean validate

  protected static final Map<String, String> EMPTY_MAP = Collections.<String, String> emptyMap()

  /**
   * mapping of xmi:id to class or interface name (e.g. EAID_71D3FE18_1F0A_49b9_BAFD_68D96B9A32C9 => Participant)
   */
  final Map<String,String> classId = new HashMap<>()

  /**
   * mapping of package element ids to package names (e.g. EAPK_E7E54777_F262_4fe3_AA8E_6E7DDA4E1CB6 => core)
   */
  final Map<String, String> packageMap = new HashMap<>()

  final File file
  final String shortTitle, overviewTitle
  final def connectors
  final def elementContainer
  final def model

  private final LinkedList<String> packageCtx = new LinkedList<>()

  final Set<String> referencedClasses = new HashSet<>()
  final Map<String, List<Aggregate>> aggregation = new HashMap<>()
  final Map<String, String> multiplicity = new HashMap<>()

  final Map<String, String> propDesc = new HashMap<>()
  final Map<String, String> interfaceMap = new HashMap<>()
  final Map<String, Set<String>> parents = new HashMap<>()
  final Map<String, Object> elements = new LinkedHashMap<>()
  final Set<String> packages = new TreeSet<>()
  final List<String> interfaces = new ArrayList<>()
  final List<String> classes = new ArrayList<>()
  final Map namespaceMapping
  // private boolean dumpDebugMode // debug

XmiParser(File file) {
  def xmlSlurper = new XmlSlurper()
  def root = xmlSlurper.parse(file)
  def umlNs = root.lookupNamespace('uml')
  namespaceMapping = [ xmi: 'http://schema.omg.org/spec/XMI/2.1' ]
  if ('http://schema.omg.org/spec/UML/2.2' == umlNs) {
    namespaceMapping['uml'] = 'http://schema.omg.org/spec/UML/2.2' // e.g. RIM XMI
  } else {
    namespaceMapping['uml'] = 'http://schema.omg.org/spec/UML/2.1' // default
  }
  root = root.declareNamespace(namespaceMapping)
  model = root.'uml:Model'

  if (!model) {
    throw new IOException('no Model element') // no data
  }

  this.file = file

  def parentElement = model.packagedElement[0]
  def modelPackage
  while(true) {
    // QIDAM structure:
    // <uml:Model xmi:type="uml:Model" name="EA_Model" visibility="public">
    //  <packagedElement xmi:type="uml:Package" xmi:id="EAPK_2663152C_4446_4c85_B11F_A256116CC577" name="QIDAM Class Model" visibility="public">
    //   <packagedElement xmi:type="uml:Class" xmi:id="EAID_B788C777_5DFE_419c_A7D9_1BFE0F7232AE" name="ActionNonPerformance" visibility="public">
    //
    // VMR structure:
    // <uml:Model xmi:type="uml:Model" name="EA_Model" visibility="public">
    //  <packagedElement xmi:type="uml:Package" xmi:id="EAPK_3E542AD4_7223_4ac7_B65F_51CB72FDD3A8" name="Model" visibility="public">
    //    <packagedElement xmi:type="uml:Package" xmi:id="EAPK_419F86AE_3D4A_418b_AC0E_D6E280137931" name="modelParent" visibility="public">
    //      <packagedElement xmi:type="uml:Package" xmi:id="EAPK_A5B8A5EA_E063_4079_A66B_7F4407B0F8CC" name="vmr" visibility="public"> **
    //        <packagedElement xmi:type="uml:Class" xmi:id="EAID_1F2792F1_E7FE_4289_AF40_EB0D29050437" name="AbstractCondition" visibility="public" isAbstract="true">
    //      <packagedElement xmi:type="uml:Package" xmi:id="EAPK_6E646EC9_3CC5_4ada_AAC6_3124F1E04EC9" name="dataTypes" visibility="public"> **
    //        <packagedElement xmi:type="uml:Class" xmi:id="EAID_5E72FFE9_A309_4a68_A3F1_0E94CC393AA0" name="AD" visibility="public">
    // NOTE: VMR has multiple packagedElement containers under the modelParent.
    def next = parentElement.packagedElement[0]
    if (next.'@xmi:type' == 'uml:Package') {
      modelPackage = parentElement
      parentElement = next
    } else break
  }
  elementContainer = parentElement

  // customizes the javadoc overview title name based on file name
  if (file.getName() == 'QUICK.xmi') overviewTitle = 'QUICK Data Model'
  else if (overviewTitle == 'vmr') overviewTitle = 'VMR'
  else if (file.getName() == 'rim.xmi') overviewTitle = 'RIM'
  else if (file.getName() == 'QLIM-Transformed.xmi') overviewTitle = 'QIDAM-Transformed'
  else if (file.getName() == 'QIDAM-Transformed.xmi') overviewTitle = 'QIDAM-Transformed'
  else if (file.getName() == 'QIDAMr1.xmi') overviewTitle = 'QIDAM'
  else overviewTitle = elementContainer.@name.text()

  shortTitle = overviewTitle.replaceFirst(' .*','').toUpperCase()
  println "overviewTitle=$overviewTitle / shortTitle=$shortTitle"

  model.packagedElement.each{
    // printf 'package: %s %s%n', it.'@xmi:id', it.@name
    checkPackage(it, it.@name.text())
  }

  // ---------------------------------------------------------
  // parse descriptions for classes, interfaces, and attributes
  // initialize propDesc field
  // ---------------------------------------------------------
  def extension = root.'xmi:Extension'
  extension.elements.element.each {
    String name = it.@name.text()
    String desc = it.properties[0].@documentation.text()
    if (desc) {
      // set class/element description
      propDesc[name] = desc
    }
    it.attributes.attribute.each { attr ->
      // <documentation value="..."/>
      desc = attr.documentation[0].@value.text()
      String attrName = attr.@name.text()
      String key = "${name}.${attrName}"
      def bounds = attr.bounds
      if (bounds) {
        def lower = bounds.@lower.text()
        def upper = bounds.@upper.text()
        // e.g., GoalDescriptor.associatedCarePlan=0,*, InferableDescriptor.inferenceMethod=0,1
        if (lower && upper && !(lower == '1' && upper == '1')) {
          //printf "XXX: %s %s %s%n", attrName, lower, upper
          multiplicity[key] = "[$lower..$upper]" // 0..*, 0..1, etc.
        }
      }

      if (desc) {
        // set attribute description
        propDesc[key] = desc
      }
    } // for each attribute
  }

  // ---------------------------------------------------------
  // initialize interfaceMap + parents fields
  // ---------------------------------------------------------
  extension.primitivetypes.packagedElement[0].packagedElement[0].packagedElement.each { elt ->
    String name = elt.@name.text()
    if (name) referencedClasses.add(name) // add name to set
  }
  //referencedClasses.addAll(primitiveTypes)
  // println primitiveTypes
  // [Medication, Text, Value, Schedule, Quantity, AdministerableSubstance, BodySite, YesNo, TimePerioid, Statement, Organization, Location, TimePeriod, TimePoint, Substance, IntervalOfQuantity, Code, Entity]

  // <packagedElement xmi:type="uml:Interface" name="ConditionDescriptor" ...
  //  <ownedAttribute name="bodySite" ...
  //   <type xmi:idref="EAJava_BodySite"/>
  //
  // TODO: Participant doesn't list StatementAboutAction/@actionParticipant reference
  // <packagedElement xmi:type="uml:Class" xmi:id="EAID_71D3FE18_1F0A_49b9_BAFD_68D96B9A32C9" name="Participant" ...
  // <packagedElement xmi:type="uml:Class" name="StatementAboutAction" ...
  //  <ownedAttribute name="actionParticipant" ...
  //   <type xmi:idref="EAID_71D3FE18_1F0A_49b9_BAFD_68D96B9A32C9"/>
  // check if any attributes reference classes or interfaces
  elementContainer.packagedElement.each { elt ->
    String name = elt.@name.text()
    if (!name) return
    elt.ownedAttribute.each{ attr ->
      // println "check $name " + attr.@name
      String attrName = attr.@name.text()
      if (!attrName) return
      String idRef = attr.type.'@xmi:idref'.text()
      String ref = classId.get(idRef)
      // e.g. AdverseEvent => ActionPerformance, Person => PersonRole, Participant => PersonRole, etc.
      if (ref) referencedClasses.add(ref)
    }
  }

  connectors = extension.connectors

  printf '%nSummary: %d packages %d classes %d interfaces%n', packages.size(), classes.size(), interfaces.size()

} // XmiParser constructor

// -----------------------------------------------

void init() {
  // ---------------------------------------------------------
  // initialize interfaceMap + parents fields
  // ---------------------------------------------------------
  connectors.connector.each {
      def target = it.target
      def targetModel = target.model
      def source = it.source
      def sourceModel = source.model
      final String sourceName = sourceModel.@name.text()
      final String targetName = targetModel.@name.text()
      def linkType = it.properties.@ea_type.text() // Generalization, Realisation, Association, Aggregation

      // map parent element to subclass
      if (linkType == 'Aggregation') {
        // e.g., Activity => Performance
        String role = source.role.@name.text()
        if (role) {
          // src=ProcedureParameters target=ProcedureDescriptor role=details type=Aggregation
          //  labels  +details
          // src=Performance target=Activity role=subTask
          //  <labels lb="0..*" lt="+subTask"/>
          String label = it.documentation.@value.text()
          if (validate && !label) printf '3: Aggregation: src=%s target=%s role=%s no documentation%n', sourceName, targetName, role // model validation
          String multiplicityValue = source.type.@multiplicity.text()
          if (multiplicityValue) {
            String value = it.labels.@lb // e.g. EncounterEvent role="relatedCondition" ... <labels lb="0..*">
            if (value && value.contains('..')) {
              String key = "${targetName}.${role}"
              multiplicity[key] = "[$value]"
            }
          } // else println "connector: $targetName role=$role multiplicity=none src=$sourceName"// multiplicity assumed 1..1
          def list = aggregation.get(targetName)
          if (list == null) {
            list = new ArrayList<Aggregate>()
            aggregation.put(targetName, list)
          }
          checkAggregation(role, targetName)
          list.add(new Aggregate(sourceName, role, label, multiplicityValue))
        } else if (validate) {
          // warning: aggregation with no defined role; e.g. Dosage -> EnteralFormula
          printf("XXX: empty source.role : Aggregation %s -> %s idref=%s targetRole=%s%n",
                  sourceName, targetName, it.'@xmi:idref', targetModel.role.@name.text())
        }
      } else if (linkType == 'Realisation' || linkType == 'Generalization') {
        // src=EncounterEvent[class] target=EncounterDescriptor[interface] type=Realisation
        // src=FamilyHistoryDescriptor[interface] target=ObservableDescriptor[interface] type=Generalization
        if (targetModel && targetModel.@type == 'Interface') {
          // e.g. interface:EncounterDescriptor => interface:ActionDescriptor [Generalization]
          //      class:ProcedureOrder => interface:Order [Realisation]
          // ContraindicationToMedication => MedicationAdministrationDescriptor [Association]
          // ContraindicationToProcedure => ProcedureDescriptor [Association]
          // e.g. ProposalFor => Proposal, ProcedureOrder => Order
          interfaceMap.put(sourceName, targetName)
        } else {
          // record class-level parents
          def parentSet = parents.get(sourceName)
          if (parentSet == null) {
            parentSet = new LinkedHashSet<String>()
            parents.put(sourceName, parentSet)
          }
          parentSet.add(targetName)
        }
      } else if (linkType == 'Association') {
        // e.g. src=EncounterEvent target=Condition type=Association
        // ContraindicationToMedication -> MedicationAdministrationDescriptor role=contraindicatedMedication
        String role = target.role.@name.text()
        if (role) {
          // if (!role) role = source.role.@name.text()
          String desc = it.documentation.@value.text()
          // printf 'connector %s -> %s role=%s desc=%s%n', sourceName, targetName, role, desc // debug
          if (desc) {
            String key = "${sourceName}.${role}"
            // printf 'XX: connector %s -> %s desc=%s%n', source, key, desc // debug
            propDesc[key] = desc
          }
        }
      }
      // other connector ClinicalStatement -> AllergyIntolerance type=Dependency
      else if ('Dependency' != linkType) {
        printf 'XX: other connector %s -> %s type=%s%n', sourceName, targetName, linkType
      } // debug (e.g. linkType=Dependency)
    }
  }

// -----------------------------------------------

void checkAggregation(String role, String targetName) {
  // empty
}

// -----------------------------------------------
@TypeChecked
void createSummary() {
  // output list of classes and interfaces
  def writer = new FileWriter(file.getName() + '-list.txt')
  elements.keySet().eachWithIndex { String name, idx ->
    if (idx != 0 && name.startsWith("package:")) writer.println()
    writer.println name
  }
  writer.close()
}

// -----------------------------------------------

void checkPackage(groovy.util.slurpersupport.NodeChild elementContainer, String packageName) {

  packageCtx.addLast(packageName)

  // <packagedElement xmi:type="uml:Package" xmi:id="EAPK_9920A293_4250_4281_9829_981965BBBD53" name="action" visibility="public">
  packageMap.put(elementContainer.'@xmi:id'.text(), packageName)

  // TODO: need to generalize the full package name for other structures as needed
  // this only works for QIDAM and QUICK with 2-level deep packages ignoring the outer most package
  if (packageCtx.size() == 3 && packageCtx.getFirst() ==~ /(QIDAM|QUICK) Class Model/) {
    packageName = packageCtx.get(1) + '/' + packageName
    // e.g. package context: [QIDAM Class Model, action, common] for Dosage class => pkg = action/common
  }

  //printf 'X: %d package: %s%n', packageCtx.size(), packageCtx // debug

  elementContainer.packagedElement.each { elt ->
    def type = elt.'@xmi:type'.text()
    if (type == 'uml:Package') {
      String name = elt.@name.text()
      // printf 'package: %s %s%n', elt.'@xmi:id', name
      if (!(shortTitle == 'VMR' && name.startsWith('cds'))) {      
        // skip VMR cdsInputSpecification packages
        checkPackage(elt, name)        
      }
      return // skip to next element
    }
    if (type == 'uml:Association') return // skip associations
	
    String name = elt.@name.text()
	
	if (!name) return // no name

	if (name == 'Schedule' && 'QUICK' == shortTitle && packageName == 'datatypes') {
		//println "skip: $packageName $name $type" // debug
        // NOTE: Schedule class exists in multiple QUICK packages (datatypes + common)
        name = "fhir:Schedule"
		//return
	}
	
	if ((shortTitle == 'QIDAM-TRANSFORMED' || shortTitle == 'QIDAM')
            && name == 'ActionModality' && packageName == 'core' )
    {
		println "skip: $packageName $name $type" // debug
		return
	}
	
    /*
    <packagedElement xmi:type="uml:Class" xmi:id="EAID_0658A714_BCB0_49e0_BE41_106F5415E52E" name="Condition" visibility="public">
      <generalization xmi:type="uml:Generalization" xmi:id="EAID_46200D60_516F_412e_834D_98B247974F9C" general="EAID_376453BD_C0D9_40c5_872F_D048DA819C2B"/>
    </packagedElement>
    */
    
    String value = elt.@visibility.text()
    // if visibility attribute not defined what is default ??
    if (value && value != 'public') println "WARN: non-public element: $name visibility=$value pkg=$packageName"

    if (packages.add(packageName)) {
      println "new package: $packageName elt=$name"
      // insert package name before each collection of elements for that package
      elements.put("package:$packageName", null)
    }

    def old = elements.put(name, elt) // classes and interfaces in order found in file	
    if (old != null) {
      println "WARN: duplicate element: $name pkg=$packageName"
    }

    final String id = elt.'@xmi:id'.text()
    
    if (overviewTitle == 'RIM') {
      // rim primitive type lookup
      //if (id) idmap.put(id, name)
      // REVIEW: is this RIM-way to do it or is the UML 2.2 way ?
      // documentation for RIM-XMI different than VMR/QIDAM/QUICK
      String desc = elt.ownedComment.body.text()
      if (desc) {
        // println "$name\n\tdesc: $desc"
        propDesc[name] = desc
      }
      //println "$name"
      elt.ownedAttribute.each{ attr ->
        String atrName = attr.@name
        if (atrName) {
          desc = attr.ownedComment.body.text()
          if (desc) {
            String key = "${name}.${atrName}"
            propDesc[key] = desc
            // println "attribute: $atrName\n\t\t$desc"
          }
        }
      }
    } // rim

    // if (elt.@isAbstract == 'true') println "$name $type abstract"
    if (type == 'uml:Interface') {
      interfaces.add(name)
    } else if (type == 'uml:Class' || type == 'uml:Enumeration') { //} || type == 'uml:PrimitiveType') {
      // NOTE: VMR uses type="uml:Enumeration"
      classes.add(name)
    } else if (type != 'uml:PrimitiveType') println "WARN: unknown element type=$type in $name"
    // e.g. uml:Signal, uml:SignalEvent

    if (id) classId.put(id, name)
  } // each element
  packageCtx.removeLast()
} // checkPackage

  @TypeChecked
  static class Aggregate {
    final String source, role, label, multiplicity
    // connector type=Aggregation;
    // examples:
    //  source ResultDetail -> target ObservationResultDescriptor[detailedResult]
    //  source ProcedureParameters -> target ProcedureDescriptor[details]

    Aggregate(String source, String role, String label, String multiplicity) {
      this.source = source // Class/Interface type of aggregate
      this.role = role     // property name in target
      this.label = label   // optional label details with multiplicity relationship (e.g. 0..*)
      this.multiplicity = multiplicity
    }
    String toString() {
      return "$source $role"
    }
  }

} // end XmiParser

// -----------------------------------------------

// end parse.groovy