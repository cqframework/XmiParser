/**
 * Created by mathews on 10/9/2014.
 */
class TestParser extends GroovyTestCase {

  void testXmiParsingWithInterfaces() {
    XmiValidator.validate = true
    def parser = new XmiParser(new File('data/QIDAM-20131217.xmi'))
    println parser.file

    // Summary: 1 packages 50 classes 33 interfaces

    parser.init()

    assert parser.shortTitle == 'QIDAM'
    assert parser.overviewTitle == 'QIDAM Class Model'

    assertFalse parser.multiplicity.isEmpty()
    assertFalse parser.propDesc.isEmpty()
    assertFalse parser.classId.isEmpty()
    assertFalse parser.aggregation.isEmpty()

    assert parser.packages.size() == 1
    assert parser.interfaces.size() == 33
    assert parser.packageMap.size() == 2
    assert parser.classes.size() == 50
    assert parser.elements.size() == 84
    assert parser.aggregation.size() == 8

    assert parser.namespaceMapping == [ 'xmi': 'http://schema.omg.org/spec/XMI/2.1', 'uml': 'http://schema.omg.org/spec/UML/2.1' ]

    assert parser.parents['Condition'] == Collections.singleton('ObservationPresence')
    assert parser.parents['MedicationAdministrationProposal'] == Collections.singleton('ActionPerformance')

    assert parser.interfaceMap['Condition'] == 'ConditionDescriptor'
    assert parser.interfaceMap['ObservationResult'] == 'ObservationResultDescriptor'

    def referencedClasses = [ 'Statement', 'Substance', 'Organization', 'Medication', 'Patient' ]
    assertTrue parser.referencedClasses.containsAll(referencedClasses)
    assertTrue parser.classes.containsAll(referencedClasses)
  }

}
