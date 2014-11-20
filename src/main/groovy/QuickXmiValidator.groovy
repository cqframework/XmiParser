/**
 * Sample custom XMI validator that validates multiple inheritance in all possible leaf-level
 * classes in the HL7 QUICK data model. Some future leaf-level elements are not yet defined
 * in the model so need to check all subclasses of Act and Observable for potential conflicts.
 *
 * For latest QUICK Model check out
 * https://github.com/cqframework/OneModel/raw/master/QUICK/eap/QUICK.xmi
 *
 * @author Jason Mathews, MITRE Corporation
 * Created on 10/29/2014.
 */
public class QuickXmiValidator extends XmiValidator {

  Set<String> clinicalStatementAttrs

  QuickXmiValidator(File file) {
    super(file)
  }

  void init() {
    // if override init() must call parent init()
    super.init()
    def elt = elements.get('ClinicalStatement')
    if (elt == null) throw new IllegalStateException()
    populateElement(elt, 'ClinicalStatement')
    if (attributes.isEmpty()) throw new IllegalStateException()
    clinicalStatementAttrs = new HashSet<String>()
    // now add only attribute names (instance of String) to target set
    // e.g. topic, statementAuthor, statementSource, subject, profileId, additionalText, statementDateTime,
    attributes.each { attr ->
      if (attr instanceof String) clinicalStatementAttrs.add(attr)
    }
  }

  /**
   * QUICK uses multiple inheritance in leaf-level nodes and need to enforce that a given leaf-level node
   * (e.g. ConditionOccurrence) does not any duplicate attributes from the inherited classes. For example,
   * ConditionOccurrence which would inherit from both Condition and StatementOfOccurrence, the latter
   * of which is a subclass of  ClinicalStatement. Furthermore, if the Condition class has a subject
   * attribute and then it will conflict with the inherited subject attribute from the ClinicalStatement
   * class.
   *
   * Following method check each subclass of Act and Observable to verify if any of its attributes
   * conflict with those of ClinicalStatement. Any such duplicate would conflict the construction
   * of a leaf-level node.
   *
   * @param packageName package name
   * @param elt Element to test
   * @param name element name
   */
  void validateAttributes(String packageName, elt, String name) {
    super.validateAttributes(packageName, elt, name)
    Set<String> parentNames = parents.get(name)
    if (parentNames && (parentNames.contains('Observable') || parentNames.contains('Act'))) {
      if (!Collections.disjoint(clinicalStatementAttrs, attributes)) {
        // duplicate attributes found
        printf "DUPLICATE: %s.%s conflicts with ClinicalStatement%n", packageName, name
        // dump out all duplicate attributes
        attributes.each { attr ->
          if (clinicalStatementAttrs.contains(attr)) println "\t" + attr
        }
        println()
      }
    }
  }

}
