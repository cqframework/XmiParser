/**
 * Created by mathews on 10/8/2014.
 */
class TestValidate extends GroovyTestCase {

  void testFHIRParsing() {
    XmiValidator.validate = true
    def parser = new XmiValidator(new File('data/FHIRDatatypes.xmi'))
    println parser.file

    parser.init()

    assert parser.overviewTitle == 'datatypes'
    assert parser.shortTitle == 'DATATYPES'

    // Summary: 1 packages 53 classes 0 interface

    assertTrue parser.interfaces.isEmpty()
    assert parser.namespaceMapping == [ 'xmi': 'http://schema.omg.org/spec/XMI/2.1', 'uml': 'http://schema.omg.org/spec/UML/2.1' ]

    PrintStream oldOut = System.out
    def bos = new ByteArrayOutputStream()
    // redirect system.out to capture output
    System.setOut(new PrintStream(bos))
    try {
      parser.validate()
    } finally {
      // restore System.out
      System.setOut(oldOut)
    }

    String output = bos.toString()
    assertTrue output.contains('ERROR: attribute conflict: id Identifier Element')
  }

}