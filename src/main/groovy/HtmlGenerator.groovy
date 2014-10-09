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
class HtmlGenerator extends XmiParser {

  // http://docs.oracle.com/javase/7/docs/api/
  // interfaces, classes, etc in frameset

  static final Set dataTypes = [
      // 'Address, 'TelecomAddress,
      'Code', 'CodeableConcept', 'IntervalOfQuantity', 'Quantity', 'Text', 'TimePoint', 'TimePeriod', 'YesNo', 'Value' ]

  // NOTE: can make propTypes a localField in createDetailPages() and pass as arg to dumpAttributes()
  final def propTypes = [:]

  final Set<IndexInfo> index = new TreeSet<>()

  final Set<String> interfaceSet = new HashSet<>()

  private boolean flatListMode

  private PrintWriter outWriter

  // temp fields set in dumpAttributes
  private String lastParent
  private boolean multiParentAttributes
  private String summaryDescription

// -----------------------------------------------

  HtmlGenerator(File file) {
    super(file)
    interfaceSet.addAll(interfaces)
  }

// -----------------------------------------------

  void checkAggregation(String role, String targetName) {
    // add aggregate property to Index
    index.add(new IndexInfo(role, targetName + ".html#$role",
            "Aggregate field in <a href='pages/${targetName}.html'>$targetName</a>"))
  }

// -----------------------------------------------

  void init() {
    super.init()

    if (shortTitle == 'QUICK')
      summaryDescription = 'Quality Information and Clinical Knowledge (QUICK) Model'
    else if (shortTitle == 'QIDAM' || shortTitle == 'QIDAM-TRANSFORMED')
      summaryDescription = '''The Health Quality Improvement Domain Analysis Model (QIDAM)
				seeks to create a conceptual data model that can be used
				to create data mapping expressions. The QIDAM harmonizes
				the existing eCQM and CDS data models into a single, unified conceptual model.
				This model can be mapped onto existing logical models while defining the structure
				and domain concepts required by eCQMs and CDS artifacts.'''
    else if (shortTitle == 'VMR')
      summaryDescription = 'HL7 Version 3 Domain Analysis Model: Virtual Medical Record (VMR) for Clinical Decision Support (vMR-CDS), Release 2'
    else summaryDescription = shortTitle
  }

// -----------------------------------------------

  void output() {
    if (!elements) {
      println "ERROR: failed to find any elements"
      return
    }

    File pageDir = new File('docs/pages')
    if (!pageDir.exists()) pageDir.mkdirs()
    // output list of classes and interfaces
    createSummary()

    outWriter = new PrintWriter(new FileWriter(file.getName() + "-details.txt")) // debug

    createAllClassesFrame()
    createOverviewSummary()
    createDetailPages()

    copyIndexTemplate()

    // createIndexPage must be called last since all other pages must be created first
    createIndexPage()

    // println "types=$types" // debug

    if (outWriter) {
      outWriter.close() // debug
    }
  }


// ---------------------------------------------------------
// create allclasses-frame page
// ---------------------------------------------------------
  void createAllClassesFrame() {
    if (packages.size() > 3) {
      createOverviewFrame()
      createPackageClassesFrames()
    }

    def writer = new FileWriter('docs/allclasses-frame.html')
    // <link rel="stylesheet" type="text/css" href="stylesheet.css" title="Style">
    def html = new MarkupBuilder(writer)

    // sample interface detail page
    // http://docs.oracle.com/javase/7/docs/api/java/awt/Transparency.html

    html.html {
      head{
        base(target:'right')
        link(rel:'stylesheet', type:'text/css', href:'stylesheet.css', title:'Style')
      }
      body{
        //<h2 >Interfaces</h2

        div(class:'indexContainer') {

          if (packages.size() > 3) {
            h1(class: "bar", 'All Classes')
          }

          boolean needClosing = false
          List<String> pkgInterfaces = new ArrayList<>()
          String pkgName
          elements.each{ String name, elt ->
            if (name.startsWith('package:')) {
              pkgName = name.substring(8)
              h2(title:'Package', pkgName)
              return
            }
            String type = elt.'@xmi:type'.text()
            if (type) {
              if (type == 'uml:Interface') {
                pkgInterfaces.add(name)
              } else if (!needClosing && type == 'uml:Class') {
                mkp.yieldUnescaped('<ul title="Classes">')
                needClosing = true
                if (packages.size() == 1) h2(title:'Classes', 'Classes')
              }
            }
            li{
              def outName = name
              // hack for QUICK data model with quick:Schedule and fhir:Schedule
              if (name.startsWith('fhir:')) outName = name.substring(5)
              a(href:"pages/${escapeName(name)}.html", outName)
            } // li
            //br()
          } // elements

          if (pkgInterfaces) {
            if (needClosing) mkp.yieldUnescaped('</ul>')
            h2(title:'Interfaces', 'Interfaces')
            ul(title:'Interfaces') {
              pkgInterfaces.each{ String name ->
                li{
                  // TODO: does package name need escaping ??
                  a(href:"pages/${name}.html") {
                    i(name)
                  }
                }
              }
            } // ul
          }

        } // div class=indexContainer
      } // body
    } // html
    writer.close()
  }

// -----------------------------------------------

  void createPackageClassesFrames() {
    // for each package create package-level allclasses page
    String pkgName
    List<String> pkgInterfaces = new ArrayList<>()
    List<String> pkgClasses = new ArrayList<>()
    elements.each{ String name, elt ->
      if (name.startsWith('package:')) {
        if (pkgName && (pkgInterfaces || pkgClasses)) {
          // dump package
          dumpPackage(pkgName, pkgInterfaces, pkgClasses)
        }
        // start new package
        pkgName = name.substring(8)
        pkgInterfaces.clear()
        pkgClasses.clear()
      } else {
        String type = elt.'@xmi:type'.text()
        if (type) {
          if (type == 'uml:Interface')
            pkgInterfaces.add(name)
          else
            pkgClasses.add(name)
        }
      }
    }

    // dump last package
    if (pkgName && (pkgInterfaces || pkgClasses)) {
      // dump package
      dumpPackage(pkgName, pkgInterfaces, pkgClasses)
    }
    else if (pkgName) println "WARN: $pkgName with no classes/interfaces?"//debug
  }

// -----------------------------------------------

  void dumpPackage(String pkgName, List pkgInterfaces, List pkgClasses) {
    def writer = new FileWriter("docs/pages/package-frame-${escapeName(pkgName)}.html")
    def html = new MarkupBuilder(writer)

    // sample interface detail page
    // http://docs.oracle.com/javase/7/docs/api/java/awt/Transparency.html

    html.html {
      head{
        base(target:'right')
        link(rel:'stylesheet', type:'text/css', href:'../stylesheet.css', title:'Style')
      }
      body{
        div(class:'indexContainer') {
          h1(class: "bar", title:'Package', pkgName)
          //h1(class: "bar", 'All Classes')

          if (pkgClasses) {
            Collections.sort(pkgClasses)
            h2(title:'Classes', 'Classes')
            ul(title:'Classes') {
              pkgClasses.each{ String name ->
                // suppress abstract Topic + Modality classes (e.g. ProcedurePerformance, ProcedureOrder, etc.)
                if (pkgName == 'statement' && (name.endsWith('Performance') || name.endsWith('Order'))) return // QIDAMr1 (Debug)
                li{
                  // detailed pages same level as package-frame pages
                  a(href:"${escapeName(name)}.html", name)
                }
              }
            } // ul
          }

          if (pkgInterfaces) {
            Collections.sort(pkgInterfaces)
            h2(title:'Interfaces', 'Interfaces')
            ul(title:'Interfaces') {
              pkgInterfaces.each{ String name ->
                li{
                  a(href:"${escapeName(name)}.html") {
                    i(name)
                  }
                }
              }
            } // ul
          }

        } // div class=indexContainer
      } // body
    } // html
    writer.close()
  }

// -----------------------------------------------

  void createOverviewFrame() {
    def writer = new FileWriter('docs/overview-frame.html')
    def html = new MarkupBuilder(writer)

    // sample interface detail page
    // http://docs.oracle.com/javase/7/docs/api/java/awt/Transparency.html

    html.html {
      head {
        base(target: 'packageFrame')
        link(rel: 'stylesheet', type: 'text/css', href: 'stylesheet.css', title: 'Style')
      }
      body {
        h1(title: overviewTitle, class: "bar") {
          strong(overviewTitle)
        }
        div(class: 'indexHeader') {
          // div(class="indexHeader"><a href="allclasses-frame.html" target="packageFrame">All Classes</a></div>
          a(href:'allclasses-frame.html', 'All Classes')
        }
        //h2('Packages')
        div(class: 'indexContainer') {
          h2(title: 'Packages', 'Packages')
          ul(title: 'Packages') {
            packages.each { String pkgName ->
              li{
                a(href : "pages/package-frame-${escapeName(pkgName)}.html", pkgName)
              }
            }
          }
        }
      } // body
    } // html
    writer.close()
  }


// ---------------------------------------------------------
// create overview-summary page
// ---------------------------------------------------------
  void createOverviewSummary() {
    def writer = new FileWriter('docs/overview-summary.html')
    def html = new MarkupBuilder(writer)
    html.html {
      // see http://docs.oracle.com/javase/7/docs/api/java/lang/package-summary.html
      head{
        //base(target:'left')
        title("Overview (${overviewTitle})")
        link(rel:'stylesheet', type:'text/css', href:'stylesheet.css', title:'Style')
      }
      body{

        mkp.yieldUnescaped('\n<!-- ========= START OF TOP NAVBAR ======= -->')
        div(class:'topNav') {
          a(name:'navbar_top') {
            mkp.yieldUnescaped('\n<!--   -->\n')
          }
          ul(class:'navList',title:'Navigation') {
            li(class:'navBarCell1Rev','Overview')
            li('Class')
            li{
              a(href:'index-files.html', 'Index')
            } // li
          } // ul
          div(class:'aboutLanguage', shortTitle)
        } // div class=topNav

        div(class:'subNav') {
          ul(class:'navList') {
            li{
              a(target:'_top', href:'index.html?overview-summary.html', 'FRAMES')
              mkp.yieldUnescaped('&nbsp;&nbsp;')
              a(target:'_top', href:'overview-summary.html', 'NO FRAMES')
            }
          }
          div{
            // script getElementById('allclasses_navbar_top')...
            mkp.yieldUnescaped('<!--   -->')
          }
        } // div class=subNav
        mkp.yieldUnescaped('<!-- ========= END OF TOP NAVBAR ========= -->')

        div(class:'header') {
          h1(title:'Package', class:'title', overviewTitle)
          div(class:'docSummary') {
            div(class:'block', summaryDescription)
          }
        }

        div(class:'contentContainer') {
          ul(class:'blockList') {
            if (shortTitle != 'QUICK') {
              li(class: 'blockList') {
                //div(class:'constantValuesContainer') {
                mkp.yieldUnescaped('''The following data types are the basic primitive types used in some fields:
<table border="0" cellpadding="3" cellspacing="0" summary="Primitive Types table">
<tr>
  <th class="colFirst" scope="col">Type</th>
  <th class="colLast" scope="col">Description</th>
</tr>
<tbody>
<tr class='altColor'>
  <td class="colFirst"><a name="Code">Code</a>
  <td class="colLast">A value taken from a controlled terminology, such as a code from LOINC
</tr>
<tr class='rowColor'>
  <td class="colFirst"><a name="CodeableConcept">CodeableConcept</a>
  <td class="colLast">A concept that may be defined by a formal reference to a terminology or ontology or may be provided by text
</tr>
<tr class='altColor'>
  <td class="colFirst"><a name="Range">Range</a>
  <td class="colLast">A range expressed over a quantity (i.e., has low and high values)
</tr>
<tr class='rowColor'>
  <td class="colFirst"><a name="Ratio">Ratio</a>
  <td class="colLast">A relationship between two Quantity values expressed as a numerator and a denominator.
</tr>
<tr class='altColor'>
  <td class="colFirst"><a name="Quantity">Quantity</a>
  <td class="colLast">A numeric value expressing an amount, with or without units
</tr>
<tr class='rowColor'>
  <td class="colFirst"><a name="Text">Text</a>
  <td class="colLast">A string of characters, formatted or unformatted for presentation
</tr>
<tr class='altColor'>
  <td class="colFirst"><a name="TimePoint">TimePoint</a>
  <td class="colLast">A particular time point that may be expressed at different levels of granularity such as date or date+time (e.g., Nov 15 2013, or Nov 15  2013 11:42:07 am EST)
</tr>
<tr class='rowColor'>
  <td class="colFirst"><a name="TimePeriod">TimePeriod</a>
  <td class="colLast">An interval of time bounded by TimePoint values indicating the beginning and the ending of the period
</tr>
<tr class='altColor'>
  <td class="colFirst"><a name="YesNo">YesNo</a>
  <td class="colLast">A value of either 'Yes' or 'No\'
</tr>
<tr class='rowColor'>
  <td class="colFirst"><a name="Value">Value</a>
  <td class="colLast">Any of the above types
</tr>
</tbody>
</table>''')
              } // li class=blockList
            }

            li(class:'blockList') {
              mkp.yieldUnescaped('''
<table class="packageSummary" border="0" cellpadding="3" cellspacing="0" summary="Class Summary table, listing classes, and an explanation">
<caption><span>Class Summary</span><span class="tabEnd">&nbsp;</span></caption>
<tr>
<th class="colFirst" scope="col">Class</th>
<th class="colLast" scope="col">Description</th>
</tr>
<tbody>''')
              classes.eachWithIndex { String className, idx ->
                String outName = className
                // hack for QUICK data model with quick:Schedule and fhir:Schedule
                if (className.startsWith('fhir:')) outName = className.substring(5)
                tr(class: idx % 2 ? 'rowColor' : 'altColor') {
                  td(class:'colFirst') {
                    a(href:"pages/${escapeName(className)}.html", outName)
                  } // td
                  td(class:'colLast') {
                    div(class:'block') {
                      String desc = propDesc[className]
                      if (desc) {
                        // custom formatting for particular models
                        if (shortTitle == 'QUICK') {
                          if ( /*packageName == 'statement' &&*/ outName.endsWith('Occurrence')) {
                            // strip examples from descriptions in the overview page (QUICK-only)
                            int ind = desc.indexOf('<b>Example</b>')
                            if (ind == -1 && outName.startsWith('MedicationTreatment'))
                              ind = desc.indexOf('<b>Example 1</b>') // QUICK
                            if (ind > 0) desc = desc.substring(0, ind)
                          }
                        }

                        desc = desc.replaceAll("${outName}\\b", "<code>$outName</code>")
                        // TODO: for QUICK need to strip out Example: snippet to make for a short readable class summary
                        //if (!desc) mkp.yieldUnescaped("&nbsp;")
                        //else
                        //if (desc.contains('<li>'))
                        mkp.yieldUnescaped(desc)
                        //else mkp.yield(desc)
                      }
                    } // div
                  } //td
                } // tr
              } // each class

              mkp.yieldUnescaped('</tbody></table>')
              //p('Class Summary')
              //p('Class	Description')
              //p('Interface Summary')
            } // li

            if (!interfaces.isEmpty()) {

              li(class:'blockList') {
                mkp.yieldUnescaped('''
<table class="packageSummary" border="0" cellpadding="3" cellspacing="0" summary="Interface Summary table, listing interfaces, and an explanation">
<caption><span>Interface Summary</span><span class="tabEnd">&nbsp;</span></caption>
<tr>
<th class="colFirst" scope="col">Interface</th>
<th class="colLast" scope="col">Description</th>
</tr>
<tbody>''')

                interfaces.eachWithIndex { className, idx ->
                  tr(class: idx % 2 ? 'rowColor' : 'altColor') {
                    td(class:'colFirst') {
                      a(href:"pages/${escapeName(className)}.html", className)
                    } // td
                    String desc = propDesc[className]
                    td(class:'colLast') {
                      div(class:'block')
                      if (desc) {
                        //if (!desc) mkp.yieldUnescaped("&nbsp;")
                        //desc = desc.replace(className, "<code>$className</code>")
                        desc = desc.replaceAll("${className}\\b", "<code>$className</code>")
                        //else
                        //if (desc.contains('<li>'))
                        mkp.yieldUnescaped(desc)
                        //else mkp.yield(desc)
                      }
                    } //td
                  } // tr
                } // each Interface
                mkp.yieldUnescaped('</tbody></table>')
                // p('Interface	Description')
              } // li
            } // !interfaces.isEmpty()

          } // ul

          //div(class:'footer') {
          div(class:'subTitle') {
            div(class:'block', "This document is the class specification for $shortTitle")
          }
          //}

        } // div class=contentContainer

        //p()
        mkp.yieldUnescaped('\n<!-- ========= START OF BOTTOM NAVBAR ======= -->')
        div(class:'bottomNav') {
          a(name:'navbar_bottom') {
            mkp.yieldUnescaped('\n<!--   -->\n')
          }
          ul(class:'navList',title:'Navigation') {
            li(class:'navBarCell1Rev','Overview')
            li('Class')
            li{
              a(href:'index-files.html', 'Index')
            } // li
          } // ul
          div(class:'aboutLanguage', shortTitle)
        } // div class=topNav

        div(class:'subNav') {
          ul(class:'navList') {
            li{
              a(target:'_top', href:'index.html?overview-summary.html', 'FRAMES')
              mkp.yieldUnescaped('&nbsp;&nbsp;')
              a(target:'_top', href:'overview-summary.html', 'NO FRAMES')
            }
          }
          div{
            // script getElementById('allclasses_navbar_top')...
            mkp.yieldUnescaped('<!--   -->')
          }
        } // div class=subNav
        mkp.yieldUnescaped('<!-- ========= END OF BOTTOM NAVBAR ========= -->')

      } // body
    }
    writer.close()
  } // createOverviewSummary()


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
        if (multiParentAttributes) {
          // update non-flat page w/link to flat page
          String nameOut = escapeName(name)
          File file = new File("docs/pages/${nameOut}.html")
          String content = file.getText()
          int ind = content.indexOf('>NO FRAMES</a>')
          if (ind > 0) {
            StringBuilder sb = new StringBuilder()
            sb.append(content.substring(0,ind+14))
            sb.append('&nbsp;&nbsp;')
            sb.append('<a href="').append(nameOut).append('-flat.html">[Flat view]</a>')
            sb.append(content.substring(ind+14))
            file.setText(sb.toString())
            // create flat view page
            createDetailPage(packageName, name, elt, true)
          } else println "WARN: failed to find flat target in page"
        }
      }
    } // each element
  } // createDetailPages()

// ---------------------------------------------------------
// create class detail page
// ---------------------------------------------------------
  void createDetailPage(String packageName, String name, elt, boolean flatList) {

    String type = elt.'@xmi:type'.text()
    boolean isInterface = type == 'uml:Interface'

    String nameOut = escapeName(name) // e.g. for RIM with <'s in names
    flatListMode = flatList
    if (flatList) nameOut += '-flat'
    def writer = new FileWriter("docs/pages/${nameOut}.html")
    def html = new MarkupBuilder(writer)
    html.html {
      head {
        link(rel:'stylesheet', type:'text/css', href:'../stylesheet.css', title:'Style')
      }
      body {

        createDetailNavbar(html, name, flatList)

        mkp.yieldUnescaped('\n<!-- ======== START OF CLASS DATA ======== -->')
        div(class:'header') {
          //div(class:'subTitle')
          def typeName = isInterface ? 'Interface' : 'Class'
          //def eltLabel = name
          //if (elt.@isAbstract == 'true') eltLabel += " (Abstract)"

          // package??
          if (packageName) div(class:"subTitle", packageName)
          String outName = name
          // hack for QUICK data model with quick:Schedule and fhir:Schedule
          if (name.startsWith('fhir:')) outName = name.substring(5)
          h2(title:"$typeName $name", class:'title', "$typeName $outName")
/*
 	h2{
		// name.replace(' ','+')
		a(name:name, (isInterface ? 'Interface ' : 'Class ') + name)
	}
   */
        } // div class=header
        // p(name)

        // <packagedElement xmi:type="uml:Class"
        // <packagedElement xmi:type="uml:Interface"

        //Parent class: <Parent class>

        div(class:'contentContainer') {
          Set<String> parentNames = parents.get(name)
          if (!isInterface && parentNames) {
            // UML can allow multiple class inheritance so need to check for multiple parents
            ul(class:'inheritance') {
              li{
                mkp.yield('Parent class' + (parentNames.size() == 1 ? '' : 'es') + ': ')
                //if (parentNames.size() > 3) println "XYZ: check $name parents"
                parentNames.eachWithIndex { parentName, idx ->
                  if (idx) mkp.yield(', ')
                  a(href:"${escapeName(parentName)}.html", parentName)
                }
              } // li
            } // ul
          } // if class

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

          Collection namedAttrs = new ArrayList()
          Set subinterfaces = new TreeSet()

          div(class:'description') {
            ul(class:'blockList') {
              li(class:'blockList') {

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

                //? def subclasses
                if (!isInterface) {
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
                } // if class

                // class or interface
                if (subinterfaces) {
                  dl {
                    dt {
                      mkp.yield(isInterface ? 'All Superinterfaces:' : 'Implemented interfaces:')
                    }
                    dd{
                      subinterfaces.eachWithIndex { def entry, int idx ->
                        if (idx) mkp.yield(', ')
                        // TODO: escape entry ??
                        a(href:"${entry}.html", entry)
                      }
                    } // dd
                  } // dl
                } // if subinterfaces

                if (isInterface) {
                  if (interfaceList) {
                    // if(subinterfaces) println "XXX: interface $name has super and sub-interfaces" // e.g. Proposal, InferableDescriptor
                    // e.g. Interface java.awt.event.ActionListener extends EventListener [Superinterfaces]
                    // interface Action [sub-interface] extends ActionListener [Superinterfaces]
                    // interface ProcedureOrder extends Order
                    dl {
                      dt('All Known Subinterfaces:')
                      dd {
                        interfaceList.eachWithIndex { srcName, idx ->
                          if (idx) mkp.yield(', ')
                          // TODO: escape srcName ??
                          a(href:"${srcName}.html", srcName)
                        }
                      }
                    } // dl
                  } // if interfaceList

                  /*
                 // implementing classes
                 // println "interface: $name"
                  //interface: Proposal
                  //classes: CommunicationProposal, EncounterProposal, ImmunizationRecommendation, MedicationAdministrationProposal, ProcedureProposal
                 refs = connectors.connector.findAll {                  =
                   it.target.model.@name == name && it.source.model.@type == 'Class'
                 }
                 refs.each{ println "\t" + it.source.model.@name }
                 // already added to classList
                 */

                  if (classesList) {
                    dl {
                      // ?? Order interface implementing class = EncounterRequest ??
                      dt('All Known Implementing Classes:')
                      dd {
                        classesList.eachWithIndex { srcName, idx ->
                          if (idx) mkp.yield(', ')
                          a(href:"${srcName}.html", srcName)
                        }
                      }
                    } // dl
                  } // if classesList

                  // if (!classesList && !interfaceList) println "XX: interface $name has no implementing classes or sub-interfaces"
                } else {
                  // otherwise class
                  // e.g. class: Statement <= { StatementAboutInference, StatementAboutObservation, StatementAboutAction }
                  // println "class: $name" // debug
                  def subClasses = connectors.connector.findAll { conn ->
                    /*
                     if connection role has Association type then it's not a subclass relation but a containership relationship
                     example:
                     1. connector src=ConditionPresent[class] target=ConditionDescriptor[interface]
                       <properties ea_type="Realisation">

                     2. connector src=ConditionPresent[class] target=PhenomenonPresence[class] (superclass)
                       ea_type="Generalization"

                     3. connector src=EncounterEvent[class] target=Condition[class]
                     <target...>
                       <role name="dischargeDiagnosis" visibility="Public" targetScope="instance"/>
                       <type multiplicity="0..*" aggregation="none" containment="Unspecified"/>
                     </target>
                     <properties ea_type="Association" direction="Source -&gt; Destination"/>
                    */
                    conn.target.model.@name == name &&
                            conn.source.model.@type == 'Class' &&
                            conn.properties.@ea_type == 'Generalization'
                    //conn.properties.@ea_type != 'Association'
                    // also NOT Medication <= MedicationIngredient [Aggregation]
                  }
                  if (! subClasses.isEmpty()) {
                    dl {
                      dt {
                        mkp.yield('Direct Known Subclasses:')
                      }
                      dd{
                        Set<String> names = new TreeSet<>()
                        subClasses.each{ conn ->
                          names.add(conn.source.model.@name.text())
                        }
                        names.eachWithIndex{ String hrefName, int idx ->
                          if (idx) mkp.yield(', ')
                          //def href = entry.source.model.@name
                          // printf "XX: %s <= %s%n", name, href
                          // if (hrefName =~ /[<>&\s]/) println "ERROR: bad href: $hrefName" // debug
                          a(href:"${hrefName}.html", hrefName)
                        }
                      } // dd
                    } // dl
                  } // if refs
                } // if class

                /*
                // e.g. ObservableDescriptor implementing interface = ConditionDescriptor
                connectors.connector.each {
                  // find All Known Implementing Classes
                  def target = it.target.model
                  def source = it.source.model.@name.text()
                  def value = target.@name.text()
                  if (target && target.@type =='Interface')  {
                    interfaceMap[source] = value
                  }
               }
               */
                //}
                //} // dl

                // flag abstract classes
                // interfaces are implicitly abstract and adding that modifier makes no difference
                if (!isInterface && elt.@isAbstract == 'true') {
                  def visibility = elt.@visibility.text()
                  StringBuilder sb = new StringBuilder()
                  if (visibility) sb.append(visibility).append(' ')
                  sb.append('abstract class')
                  //sb.append(isInterface ? 'interface' : 'class')
                          .append(' <span class="strong">')
                          .append(name)
                          .append('</span>')
                  if(parentNames && !isInterface) {
                    sb.append('\nextends')
                    parentNames.eachWithIndex{ String parentName, int idx ->
                      if (idx) sb.append(',')
                      sb.append(' <a href="').append(parentName)
                              .append('.html">')
                              .append(parentName)
                              .append('</a>')
                    }
                  }
                  pre{
                    mkp.yieldUnescaped(sb.toString())
                  }
                } // abstract class ??

                // <Description from XMI>
                String desc = propDesc[name]
                if (desc) {
                  if (shortTitle == 'QUICK') {
                    if (packageName == 'statement' && name.endsWith('Occurrence')) {
                      //String target = name.startsWith('MedicationTreatment') ? '<b>Example 1</b>' : '<b>Example</b>'
                      //String old = desc
                      int ind = desc.indexOf('<b>Example</b>')
                      if (ind == -1) ind = desc.indexOf('<b>Example 1</b>')
                      // format Occurrence detail pages adding <pre> block around examples
                      if (ind > 0) desc = desc.substring(0,ind) + '<P><pre>' + desc.substring(ind) + '</pre>'
                      //desc = desc.replaceFirst(target, "<pre>" + target)
                      //if (old.length() != desc.length()) desc += '</pre>'
                      // desc = '<pre>' + desc + '</pre>'
                    } // QUICK hack

                    //desc = desc.replace(name, "<code>$name</code>")
                    desc = desc.replaceAll("${name}\\b", "<code>$name</code>")
                  }
                  //if (desc.contains('<li>'))
                  mkp.yieldUnescaped(desc)
                  //else mkp.yield(desc)
                } // else printf '1: %s: %s no description%n', type, name // model validation (old)

                /*
                // This resource is referenced by CarePlan and Condition
                BodySite is referenced:  ConditionDescriptor, Dosage, ObservationResultDescriptor, ProcedureDescriptor
                Location is referenced:  EncounterDescriptor
                Medication is referenced: MedicationAdministrationDescriptor
                Organization is referenced: EncounterDescriptor
                Schedule is referenced:  Dosage, EncounterDescriptor, ProcedureDescriptor
                Statement is referenced: InferableDescriptor
                Substance is referenced: OrganismSensitivity
                */

                checkReferences(html, name, elt, flatList)

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
                  }
                  //subinterfaces.clear()
                }

              } // li class=blockList
            } // ul class=blockList
          } // div class=description

          if (namedAttrs) {
            def sortedAttrMap = new TreeMap<String, String>() // debug
            if (!flatList && outWriter) {
              //def sep = '==================================================================='
              //outWriter.printf('%s%n%s%n%s%n%n', sep, name, sep) // debug
              outWriter.printf('%n%s%n', name) // debug
            }

            boolean needclose = false

            div(class:'summary') {
              ul(class:'blockList') {
                li(class:'blockList') {
                  ul(class:'blockList') {
                    li(class:'blockList') {
                      mkp.yieldUnescaped('\n<!-- =========== FIELD SUMMARY =========== -->')
                      a(name:'field_summary') {
                        mkp.yieldUnescaped('<!--   -->')
                      }
                      h3('Field Summary')
                      //<table class="overviewSummary" border="0" cellpadding="3" cellspacing="0" summary="Field Summary table, listing fields, and an explanation">
                      //<caption><span>Fields</span><span class="tabEnd">&nbsp;</span></caption>

                      if (flatList) {
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

                        if (!namedAttrs.isEmpty()) {
                          mkp.yieldUnescaped('''\n<table class="overviewSummary" border="0" cellpadding="3" cellspacing="0"
 summary="Field Summary table, listing fields, and an explanation">
<caption><span>Fields</span><span class="tabEnd">&nbsp;</span></caption>
<tr>
<th class="colFirst" scope="col">Field</th>
<th class="colLast" scope="col">Type and Description</th>
</tr>
''')
                          needclose = true
                        }
                      } // flatList

                      int count = 0
                      // TODO add a name to detail pages for fields
                      // namedAttrs entries are a Attribute object followed by sequence of attribute names (strings)
                      String parentName
                      namedAttrs.each { attr ->
                        if (attr instanceof groovy.util.slurpersupport.Attributes) {
                          if (needclose) mkp.yieldUnescaped('\n</table><P/>')
                          count = 0
                          parentName = attr.text()
                          if (parentName != name) {
                            mkp.yield 'Fields inherited from'
                            a(href: "${parentName}.html") {
                              if(interfaceSet.contains(parentName))
                                i(parentName) // interface use italics
                              else
                                mkp.yield parentName
                            }
                          }
                          mkp.yieldUnescaped('''\n<table class="overviewSummary" border="0" cellpadding="3" cellspacing="0"
 summary="Field Summary table, listing fields, and an explanation">
<caption><span>Fields</span><span class="tabEnd">&nbsp;</span></caption>
<tr>
<th class="colFirst" scope="col">Field</th>
<th class="colLast" scope="col">Type and Description</th>
</tr>
''')
                          needclose = true
                        } else {
                          // add attribute to sorted set for file output
                          // class/interface name
                          // attribute - [inherited from class/interface] description
                          if (flatList) parentName = sortedAttrMap.get(attr)
                          else if (outWriter) {
                            String oldValue = sortedAttrMap.put(attr, parentName)
                            if (oldValue != null&& oldValue != parentName) outWriter.println "\tERROR: attribute conflict: $attr $oldValue $parentName" // debug
                          }

                          tr(class:(count++ % 2 == 0 ? 'altColor' : 'rowColor')) {
                            String propType = propTypes[attr]
                            if (propType == null) propType = 'type'
                            td(class:'colFirst') {
                              strong{
                                if (flatList && parentName != name)
                                  a(name:attr) {
                                    a(href:"${escapeName(parentName)}.html#$attr", attr)
                                  }
                                else
                                  a(name:attr, attr)
                              }
                            }

                            td(class:'colLast') {
                              String outType = propType
                              // hack for QUICK data model with quick:Schedule and fhir:Schedule
                              if (outType.startsWith('fhir:')) outType = outType.substring(5)
                              if (elements.containsKey(propType)) {
                                a(href: "${escapeName(propType)}.html") {
                                  code(outType)
                                }
                              } else if (dataTypes.contains(propType))
                                a(href:"../overview-summary.html#${propType}") {
                                  code(outType)
                                }
                              else {
                                // printf "XX: %-39s type=%s%n", name + "." + attr, propType // debug
                                code(propType)
                              }
                              String key = "${parentName}.${attr}"
                              String multiplicityValue = multiplicity.get(key) // e.g. [0,*] or [1,*] or [1]
                              if (multiplicityValue && multiplicityValue != '[1]') code(multiplicityValue)
                              // to suppress description in detail pages need to comment out following code
                              String desc = propDesc[key]
                              if (desc) {
                                if ((shortTitle == 'QIDAM-TRANSFORMED' || shortTitle == 'QIDAM')
                                        && packageName == 'observable' && name != 'ResultGroup') {
                                  // add custom links to class references: fixes typo in model docs
                                  //desc = desc.replaceFirst('ObservationResultGroup', '<a href="ResultGroup.html">ResultGroup</a>')
                                  desc = desc.replaceFirst('ResultGroup', '<a href="ResultGroup.html">ResultGroup</a>')
                                }
                                if (shortTitle == 'QUICK') {
                                  desc = desc.replaceFirst('\\b(http://[^\\s)\\]]+)','<a href="$1">$1</a>')
                                }
                                blockquote{
                                  div(class:'block') {
                                    if (desc =~ /<[a-zA-Z].*?>/) // desc.contains('<'))
                                      mkp.yieldUnescaped(desc) // markup in text (e.g., <P>, <pre>, etc).
                                    else mkp.yield(desc)
                                  } // div
                                }
                              } // desc?
                              else if (validate && !flatList && parentName.startsWith(name)) {
                                printf '2: property: %s.%s no description%n', parentName, attr // model validation
                              }
                            } // td
                          } // tr
                        }
                      } // foreach namedAttrs
                    } // li:blockList
                  } // ul:block:List
                } // li:blockList
              } // ul:block:List
            } // div summary

            if (outWriter && !flatList) {
              sortedAttrMap.each { attr, parent -> // debug
                if (parent == name)
                  outWriter.printf('\t%-23s : %s%n', attr, propTypes[attr])
                else
                  outWriter.printf('\t%-23s : %-18s [%s]%n', attr, propTypes[attr], parent)
                //outWriter.printf('\t%s (%s)\t%s%n', attr, parent, propTypes[attr])
                //String key = "${parentName}.${attr}"
                //String desc = propDesc[key]
                //if (desc) println desc
              }
            }

            // namedAttrs + propTypes maps must be cleared for each element
            namedAttrs.clear()
            propTypes.clear()

            if (needclose) mkp.yieldUnescaped('</table>')
          } // if namedAttrs
          // else println "XXX: $name has no properties" // debug validation no fields referenced, inherited or direct (E.g. BodySite, Entity, Location, Schedule, Device, etc.)
        } // div class=contentContainer

        mkp.yieldUnescaped('<!-- ========= END OF CLASS DATA ========= -->')

        // else println "UNK" // e.g. xmi:type="uml:Association"

      } // end body
    } // end html
    writer.close()
  } // createDetailPage()

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


// ---------------------------------------------------------
// create top navpage for detailed pages
// ---------------------------------------------------------
  void createDetailNavbar(html, String name, boolean flatList) {
    // create top navbar HTML for pages/xxx.html
    html.mkp.yieldUnescaped('\n<!-- ========= START OF TOP NAVBAR ======= -->')
    html.div(class:'topNav') {
      a(name:'navbar_top') {
        mkp.yieldUnescaped('\n<!--   -->\n')
      }

      /*
      a(href:"#skip-navbar_top", title:"Skip navigation links")
      a(name:"navbar_top_firstrow") {
            mkp.yieldUnescaped('\n<!--   -->\n')
      }
      */

      ul(class:'navList', title:'Navigation') {
        li{
          a(href:'../overview-summary.html', 'Overview')
        } // li
        li(class:'navBarCell1Rev','Class')
        li{
          a(href:'../index-files.html', 'Index')
        } // li
      } // ul
      div(class:'aboutLanguage', shortTitle)
    } // div class=topNav

    html.div(class:'subNav') {
      /*
      ul(class:'navList') {
          //li()
          mkp.yieldUnescaped('\n<!--   -->') // Prev/Next
      }
      */
      name = escapeName(name) // e.g. for RIM with <'s in names
      ul(class:'navList') {
        li{
          String nameOut = name
          if (flatList) nameOut += '-flat'
          a(target:'_top', href:"../index.html?pages/${nameOut}.html", 'FRAMES')
          mkp.yieldUnescaped('&nbsp;&nbsp;')
          a(target:'_top', href:"${nameOut}.html", 'NO FRAMES')
          if (flatList) {
            mkp.yieldUnescaped('&nbsp;&nbsp;')
            a(href: "${name}.html", '[Hierarchy view]')
          }
        }
      }
      /*
       ul(class:'navList', id:'allclasses_navbar_top') {
           // li()
           mkp.yieldUnescaped('\n<!--   -->')
       }
       */
      // ul(class:'navList',id:'allclasses_navbar_bottom')
      // div() // script

      div{
        // script getElementById('allclasses_navbar_top')...
        mkp.yieldUnescaped('<!--   -->')
      }
    } // subNav
    html.mkp.yieldUnescaped('\n<!-- ========= END OF TOP NAVBAR ========= -->')
  } // createDetailNavbar()


// ---------------------------------------------------------
// create reference list for detailed pages
// ---------------------------------------------------------
  private void checkReferences(html, String name, elt, boolean flatList) {
    def refs
    Map references = new LinkedHashMap()

    refs = connectors.connector.findAll {
      // TODO: this only checks for aggregations for the given class not its superclasses or interfaces
      it.source.model.@name == name && it.properties.@ea_type == 'Aggregation'
    }
    if (!refs.isEmpty()) {
      // Activity <= Performance, ConditionDetail <= ConditionDescriptor, Device <= DeviceApplicationDescriptor,
      // MedicationIngredient <= Medication, Vaccine <= ImmunizationDescriptor, ...
      refs.each { connector ->
        String targetName = connector.target.model.@name.text()
        String role = connector.source.role.@name.text()
        if (role && targetName) {
          def attrs = new Attributes(
                  new Attribute('name', role, elt, '', EMPTY_MAP), 'ownedAttribute', EMPTY_MAP)
          references.put(targetName, attrs)
          // e.g. EncounterCondition EncounterEvent.relatedCondition
          // printf 'X: Aggregation: %-20s ref=%s.%s%n', name, targetName, role
        }
      }
    }

    if (referencedClasses.contains(name)) {
      String targetType = "EAJava_$name"
      String idRef = elt.'@xmi:id'.text()
      //if (name == "Person") println "$name $idRef" // debug
      //elements.each { parent ->
      int refCount = references.size()
      elementContainer.packagedElement.each { parent ->
        //println "XXX check " + parent.@name
        // parent -> groovy.util.slurpersupport.NodeChild
        //println name
        //for(Iterator nodeit = parent.childNodes(); nodeit.hasNext(); ){
        //def node = nodeit.next()
        //if (node.name() == "ownedAttribute")
        //println node.type."@xmi:idref"
        //}
        def attrs = parent.ownedAttribute.findAll { attr ->
          def attrType = attr.type.'@xmi:idref'.text()
          (attrType == targetType || attrType == idRef) && attr.@name.text()
          // <packagedElement xmi:type="uml:Class" name="BodySite" ...
          // <packagedElement xmi:type="uml:Interface" name="ConditionDescriptor" ...
          //   <ownedAttribute name="bodySite" ...
          //     <type xmi:idref="EAJava_BodySite"/>
          //
          // <packagedElement xmi:type="uml:Class" xmi:id="EAID_6A86633B_F303_40c9_807B_FD0283303582" name="PersonRole"...
          // <packagedElement xmi:type="uml:Class" xmi:id="EAID_05586DFB_272E_47dd_99D8_1B0E840F3A2D" name="Person" ...
          //   <ownedAttribute name="role" ...
          //     <type xmi:idref="EAID_6A86633B_F303_40c9_807B_FD0283303582"/>
          //
          // e.g. StatementAboutAction/@actionParticipant references Participant class via attribute
          // <packagedElement xmi:type="uml:Class" xmi:id="EAID_71D3FE18_1F0A_49b9_BAFD_68D96B9A32C9" name="Participant" ...
          // <packagedElement xmi:type="uml:Class" name="StatementAboutAction" ...
          //   <ownedAttribute name="actionParticipant" ...
          //     <type xmi:idref="EAID_71D3FE18_1F0A_49b9_BAFD_68D96B9A32C9"/>
        }
        //attrs -> groovy.util.slurpersupport.NoChildren or groovy.util.slurpersupport.NodeChild
        //println "XX:" + attrs.getClass().getName()
        if (!attrs.isEmpty()) {
          // printf "REF: %d. %-22s\tRef=%s%n", references.size(), name, parent.@name // debug
          references.put(parent.@name.text(), attrs)
        }
      } // each element in model

      if (!flatList && refCount == references.size()) {
        println "INFO: no references for $name"
      }

      // TODO: show associations and aggregations
      // e.g. class Vaccine <= [association] interface ImmunizationDescriptor
    } // referencedClasses.contains(name)

    if (references) {
      // println "$name is referenced" // e.g. BodySite, EncounterEvent, Medication, etc.
      html.ul(class: 'blockList') {
        li(class: 'blockList') {
          h3('Reference Summary')
          mkp.yield('This resource is referred to by the following:')
          mkp.yieldUnescaped('''\n<table class="overviewSummary" border="0" cellpadding="3" cellspacing="0"
   summary="Reference Summary table, listing classes and properties that reference this resource">
  <caption><span>References</span><span class="tabEnd">&nbsp;</span></caption>
  <tr>
  <th class="colFirst" scope="col">Class or Interface</th>
  <th class="colLast" scope="col">Property</th>
  </tr>
  ''')

          references.eachWithIndex { refName, attrs, idx ->
            // println "XXX: $refName" // debug
            html.tr(class: (idx % 2 == 0 ? 'altColor' : 'rowColor')) {
              td {
                a(href: "${refName}.html", " " + refName)
              }
              td {
                attrs.eachWithIndex { attr, atrIdx ->
                  if (atrIdx != 0) mkp.yield(', ') //mkp.yield(atrIdx == lastIdx ? " and " : ', ')
                  def attrName = attr.@name.text()
                  a(href: "${refName}.html#${attrName}", " " + attrName)
                  // check multiplicity
                  // TODO: reference EncounterEvent.relatedCondition in EncounterCondition not shown as 0..* ?
                  // e.g. QLIM -- ConditionDetail ref: Condition conditionDetail 0..*
                  String key = "${refName}.${attrName}"
                  String multiplicityValue = multiplicity.get(key) // e.g. [0,*] or [1,*] or [1]
                  if (multiplicityValue && multiplicityValue != '[1]') {
                    code(multiplicityValue)
                    // println "XXX: $name ref: $refName $attrName $multiplicityValue"
                  }
                  //mkp.yield(attr.@name)
                }
              } // td
            }// tr
          }
          mkp.yieldUnescaped('</table>')
        } // li
      } // ul
    }
  } // checkReferences()

// ---------------------------------------------------------

  @TypeChecked
  void copyIndexTemplate() {
    String body = (new File(packages.size() > 3 ? 'template/index2.html' : 'template/index.html')).text.replaceFirst('%TITLE%', shortTitle)
    new File('docs/index.html').setText(body)
    copyFile('template/stylesheet.css', 'docs/stylesheet.css')
    File dir = new File('docs/resources')
    if (!dir.exists() && dir.mkdir()) {
      copyFile('template/resources/background.gif',   'docs/resources/background.gif')
      copyFile('template/resources/tab.gif',          'docs/resources/tab.gif')
      copyFile('template/resources/titlebar.gif',     'docs/resources/titlebar.gif')
      copyFile('template/resources/titlebar_end.gif', 'docs/resources/titlebar_end.gif')
    }
  }


// ---------------------------------------------------------
// create index page
// ---------------------------------------------------------
  void createIndexPage() {
    def writer = new FileWriter('docs/index-files.html')
    def html = new MarkupBuilder(writer)

// sample interface detail page
// http://docs.oracle.com/javase/7/docs/api/java/awt/Transparency.html

    html.html {
      head{
        title("A-Z Index ($shortTitle)")
        link(rel:'stylesheet', type:'text/css', href:'stylesheet.css', title:'Style')
      }
      body{

        mkp.yieldUnescaped('\n<!-- ========= START OF TOP NAVBAR ======= -->')
        div(class:'topNav') {
          a(name:'navbar_top') {
            mkp.yieldUnescaped('\n<!--   -->\n')
          }
          ul(class:'navList',title:'Navigation') {
            li{
              a(href:'overview-summary.html', 'Overview')
            } // li
            li('Class')
            li(class:"navBarCell1Rev") {
              mkp.yield('Index')
            } // li
          } // ul
          div(class:'aboutLanguage', shortTitle)
        } // div class=topNav

        div(class:'subNav') {
          ul(class:'navList') {
            li{
              a(target:'_top', href:'index.html?index-files.html', 'FRAMES')
              mkp.yieldUnescaped('&nbsp;&nbsp;')
              a(target:'_top', href:'index-files.html', 'NO FRAMES')
            }
          }
          div{
            // script getElementById('allclasses_navbar_top')...
            mkp.yieldUnescaped('<!--   -->')
          }
        } // div class=subNav
        mkp.yieldUnescaped('\n<!-- ========= END OF TOP NAVBAR ========= -->')

        div(class:'contentContainer') {
          final Set<Character> keyset = new HashSet<>()
          index.each{
            keyset.add(Character.toUpperCase(it.name.charAt(0)))
          }

          // A-Z short-cut at top
          for(Character c in 'A'..'Z') {
            if (c != 'A')
              mkp.yieldUnescaped('&nbsp;')
            if (keyset.contains(c))
              a(href:"#$c", c)
            else
              mkp.yield(c)
          }

          // TODO: aggregation/association properties are not yet include in index list

          char lastRef = ' '
          index.each{
            String key = it.name
            String target = it.href
            //p(class:"strong") {
            char ref = Character.toUpperCase(key.charAt(0))
            if (ref != lastRef) {
              h2(class:'title') {
                a(name:ref, ref)
              }
              lastRef = ref
            }
            a(class:'strong', href:"pages/$target", key)
            if (it.desc) {
              mkp.yieldUnescaped(" - ${it.desc}")
            }
            br()
            //} // span
          } // each

          br()
          p()

          // A-Z short-cut at bottom
          for(Character c in 'A'..'Z') {
            if (c != 'A')
              mkp.yieldUnescaped('&nbsp;')
            if (keyset.contains(c))
              a(href:"#$c", c)
            else
              mkp.yield(c)
          }
        } // div class=contentContainer

        mkp.yieldUnescaped('\n<!-- ======= START OF BOTTOM NAVBAR ====== -->')
        div(class:'bottomNav') {
          a(name:'navbar_bottom') {
            mkp.yieldUnescaped('\n<!--   -->\n')
          }
          ul(class:'navList', title:'Navigation') {
            li{
              a(href:'overview-summary.html', 'Overview')
            } // li
            li('Class')
            li(class:'navBarCell1Rev') {
              mkp.yield('Index')
            } // li
          } // ul
          div(class:'aboutLanguage', shortTitle)
        } // div class=topNav

        div(class:'subNav') {
          ul(class:'navList') {
            li{
              a(target:'_top', href:'index.html?index-files.html', 'FRAMES')
              mkp.yieldUnescaped('&nbsp;&nbsp;')
              a(target:'_top', href:'index-files.html', 'NO FRAMES')
            }
          }
          div{
            // script getElementById('allclasses_navbar_top')...
            mkp.yieldUnescaped('<!--   -->')
          }
        } // div class=subNav
        mkp.yieldUnescaped('<!-- ======== END OF BOTTOM NAVBAR ======= -->')
        //mkp.yieldUnescaped('<dd>&nbsp;</dd>')
      }//body
    }//html
    writer.close()
  } // end createIndexPage


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
