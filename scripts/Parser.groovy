/*
 * Main script to run HTML Generator or validator
 *
 * Usage: groovy Parser.groovy [options] xmi-file
 *
 * Options:
 *  -v enable validation
 *  -s generate summary file listing all packages and classes
 *  -h generate HTML documentation
 */
boolean validate = false
boolean html = false
boolean summary = false
File file
args.each { arg ->
  if (arg.startsWith('-')) {
    if ('-v' == arg) validate = true
	else if ('-s' == arg) summary = true
    else if ('-h' == arg) html = true
    else throw new IllegalArgumentException("unknown option: $arg")
  } else {
    file = new File(arg)
  }
}

if (file == null) throw new IllegalArgumentException()
if (!file.exists()) throw new FileNotFoundException()

println "Input: $file"
if (html) {
  def parser = new HtmlGenerator(file)
  XmiParser.validate = validate
  parser.init()
  parser.output()
  def types = new ArrayList()  
  parser.types.each {
    if (!parser.elements.containsKey(it)) types.add(it)
  }
  if (types) {
    println '\nTypes:\n'
    types.each {
      println "\t$it"
    }
  }
} else {  
  XmiParser.validate = validate
  def parser = new XmiValidator(file)
  parser.init()
  if (summary) {
	parser.createSummary()
  }  
  parser.validate()
}

// -----------------------------------------------
// end main
// -----------------------------------------------