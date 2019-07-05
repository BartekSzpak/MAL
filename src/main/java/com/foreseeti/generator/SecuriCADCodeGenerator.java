package com.foreseeti.generator;

import static org.junit.Assert.assertTrue;

//Iff any attack step (internal or external) points to an inherited attack step in an asset:
//Create a specialization of that attack step, in the specialized asset.
//Override the setExpectedParents() method, including the pointing attack step as parent in the specialization.
//(In the generalized attack step, the pointing attack step should not be included).
//Don't instantiate the attack step in the generalized class but instead in the constructor.

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import se.kth.mal.Asset;
import se.kth.mal.Association;
import se.kth.mal.AttackStep;
import se.kth.mal.CompilerModel;
import se.kth.mal.steps.Step;

// The JavaWriter produces executable Java code for securiCAD simulator.
public class SecuriCADCodeGenerator {

   protected PrintWriter   writer;
   protected String        securiLangFolder;
   protected String        securiLangFile;
   protected String        testCasesFolder;
   protected String        jsonString   = "";
   protected CompilerModel model;
   protected String        packageName;
   protected String        javaFolder;
   protected File          visualFolder = null;

   protected Integer       associationIndex;

   protected String package2path(String packageName) throws IllegalArgumentException {
      if (!packageName.matches("\\w+(\\.\\w+)*$")) {
         throw (new IllegalArgumentException(String.format("'%s' is not a valid package name", packageName)));
      }

      return packageName.replace('.', File.separatorChar);
   }

   public SecuriCADCodeGenerator(File input, File output, String packageName, File iconPath) {
      this.visualFolder = iconPath;
      String packagePath = package2path(packageName);
      this.model = new CompilerModel(input);
      try {
         writeJava(output.getAbsolutePath(), packageName, packagePath);
      }
      catch (IOException e) {
         e.printStackTrace();
      }
   }

   public SecuriCADCodeGenerator(String securiLangFile, String testCasesFolder, String javaFolder, String packageName, String visualFolderPath) throws IllegalArgumentException {
      this.securiLangFile = securiLangFile;
      this.testCasesFolder = testCasesFolder;
      this.packageName = packageName;
      this.javaFolder = javaFolder;

      if (securiLangFile == null || securiLangFile.equals("")) {
         throw new IllegalArgumentException("Missing MAL file path");
      }
      if (javaFolder == null || javaFolder.equals("")) {
         throw new IllegalArgumentException("Missing java Output FolderPath file path");
      }

      File malFile = new File(securiLangFile);
      if (!malFile.exists() || !malFile.isFile()) {
         throw new IllegalArgumentException("Bad MAL file path " + malFile.getAbsolutePath());
      }

      File outPut = new File(javaFolder);
      if (outPut.exists() && !outPut.isDirectory()) {
         throw new IllegalArgumentException("\"" + javaFolder + "\" is not a directory");
      }
      else if (!outPut.exists()) {
         if (!outPut.mkdirs()) {
            throw new IllegalArgumentException("Couldn't create directory \"" + javaFolder + "\"");
         }
      }

      if (testCasesFolder != null && !testCasesFolder.equals("")) {
         File testCaseOut = new File(testCasesFolder);
         if (!testCaseOut.exists() || !testCaseOut.isDirectory()) {
            throw new IllegalArgumentException("Bad test cases output folder path");
         }
      }
      this.securiLangFolder = new File(malFile.getAbsolutePath()).getParentFile().getAbsolutePath();

      if (visualFolderPath != null) {
         this.visualFolder = new File(visualFolderPath.trim());
         if (!visualFolder.exists() || !visualFolder.isDirectory()) {
            throw new IllegalArgumentException("Bad visualization folder path");
         }
      }
   }

   private String capitalize(final String line) {
      return Character.toUpperCase(line.charAt(0)) + line.substring(1);
   }

   private String decapitalize(final String line) {
      return Character.toLowerCase(line.charAt(0)) + line.substring(1);
   }

   private String readFile(String filePath) throws IOException {
      String contents;
      try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
         StringBuilder sb = new StringBuilder();
         String line = br.readLine();

         while (line != null) {
            sb.append(line);
            sb.append(System.lineSeparator());
            line = br.readLine();
         }
         contents = sb.toString();
      }
      return contents;
   }

   protected List<String> getImportList() {
      List<String> importsList = new ArrayList<String>();
      importsList.add("com.foreseeti.simulator.*");
      importsList.add("com.foreseeti.simulator.ConcreteSample");
      importsList.add("com.foreseeti.corelib.BaseSample");
      importsList.add("com.foreseeti.corelib.FClass");
      importsList.add("com.foreseeti.corelib.FAnnotations.TypeDescription");
      importsList.add("com.foreseeti.corelib.FAnnotations.TypeName");
      importsList.add("com.foreseeti.corelib.DefaultValue");
      importsList.add("com.foreseeti.simulator.Defense");
      importsList.add("com.foreseeti.simulator.AttackStep");
      importsList.add("com.google.common.collect.ImmutableSet");
      importsList.add("com.foreseeti.simulator.MultiParentAsset");
      importsList.add("com.foreseeti.simulator.BaseLangLink");
      importsList.add("com.foreseeti.corelib.AssociationManager");
      importsList.add("com.foreseeti.corelib.util.FProbSet");
      importsList.add("com.foreseeti.corelib.util.FProb");
      importsList.add("com.foreseeti.corelib.FAnnotations.*");
      importsList.add("java.util.ArrayList");
      importsList.add("java.util.HashSet");
      importsList.add("java.util.List");
      importsList.add("java.util.Set");
      importsList.add("com.foreseeti.corelib.BaseSample");
      importsList.add("com.foreseeti.simulator.Asset");
      importsList.add("com.foreseeti.simulator.AttackStep");
      importsList.add("com.foreseeti.simulator.AttackStepMax");
      importsList.add("com.foreseeti.simulator.AttackStepMin");
      importsList.add("com.foreseeti.simulator.Defense;");
      return importsList;
   }

   protected void writeJava(String outputFolder, String packageName, String packagePath) throws IOException, UnsupportedEncodingException {

      // Create the path unless it already exists
      String path = outputFolder + "/" + packagePath + "/";
      (new File(path)).mkdirs();

      writer = new PrintWriter(path + "MetaData.java", "UTF-8");
      writeMetaData();
      writer.close();

      for (Asset asset : model.getAssets()) {
         System.out.println("Writing the Java class corresponding to asset " + asset.getName());
         String sourceCodeFile = path + asset.getName() + ".java";
         writer = new PrintWriter(sourceCodeFile, "UTF-8");
         printPackage(packageName);
         printImports();
         printAssetClassHeaders(asset);
         printDefaultClassMembers(asset);
         printAssociations(asset);
         printStepAssignments(asset);
         printConstructor(asset);
         printStepDefinitions(asset);
         printConnectionHelpers(asset);
         printDefaultOverriddenMethods(asset);
         printLocalAttackStepSpecialization(asset);
         if (visualFolder != null) {
            printIcons(asset);
         }
         writer.println("}");
         writer.close();
      }

      System.out.println("writing lang link file AutoLangLink.java");

      String sourceCodeFile = path + "AutoLangLink.java";
      writer = new PrintWriter(sourceCodeFile, "UTF-8");
      writer.println("package auto;");
      writer.println("import com.foreseeti.corelib.Link;");
      writer.println("public enum AutoLangLink implements Link {");

      String coma = "";
      for (Association association : model.getLinks().keySet()) {
         String link_text = model.getLinks().get(association);
         writer.print(coma);
         writer.println(String.format("%s(\"%s\")", link_text, association.getName()));
         coma = ",";
      }
      writer.println(";");
      writer.println("private final String name;");
      writer.println("AutoLangLink(String name) {");
      writer.println("this.name = name;");
      writer.println("}");
      writer.println("@Override");
      writer.println("public String getName() {");
      writer.println("return name;");
      writer.println("}");
      writer.println("}");
      writer.close();

      sourceCodeFile = path + "Attacker.java";
      writer = new PrintWriter(sourceCodeFile, "UTF-8");
      createDefaultAttacker();
      writer.close();

   }

   private void writeMetaData() {
      Properties meta = new Properties();
      try {
         InputStream is = this.getClass().getClassLoader().getResourceAsStream("metadata.properties");
         meta.load(is);
         is.close();
      }
      catch (IOException e) {
         e.printStackTrace();
      }

      if (!this.model.meta.containsKey("id")) {
         throw new Error("Language is missing required metadata `id`. Please define `#id: \"\"`");
      }
      if (!this.model.meta.containsKey("version")) {
         throw new Error("Language is missing required metadata `version`. Please define `#version: \"\"`");
      }
      String id = this.model.meta.get("id");
      String version = this.model.meta.get("version");

      writer.println("package auto;");
      writer.println("import java.util.HashMap;");
      writer.println("import java.util.Map;");
      writer.println("public final class MetaData {");
      writer.printf("public static final String MAL_VERSION = \"%s\";\n", meta.getProperty("version"));
      writer.printf("public static final String ID = \"%s\";\n", id);
      writer.printf("public static final String VERSION = \"%s\";\n", version);
      writer.println("public static Map<String, String> DATA;");
      writer.println("static {");
      writer.println("DATA = new HashMap<>();");
      for (Entry<String, String> entry : this.model.meta.entrySet()) {
         writer.printf("DATA.put(\"%s\", \"%s\");\n", entry.getKey(), entry.getValue());
      }
      writer.println("}");
      writer.println("}");
      System.out.printf("%s, version %s\n", id, version);
   }

   void printDefaultClassMembers(Asset asset) {
      if (asset.getSuperAssetName().isEmpty()) {
         writer.println("protected ImmutableSet<AttackStep> attackSteps;");
         writer.println("protected ImmutableSet<Defense> defenses;");
      }
   }

   void printDefaultOverriddenMethods(Asset asset) {
      writer.println("@Override");
      writer.println("public ImmutableSet<AttackStep> getAttackSteps() {");
      writer.println("return attackSteps;");
      writer.println("}");
      writer.println("@Override");
      writer.println("public ImmutableSet<Defense> getDefenses() {");
      writer.println("return defenses;");
      writer.println("}");
      writer.println("@Override");
      writer.println("public String getDescription() {");
      writer.printf("return \"%s\";\n", asset.getInfo());
      writer.println("}");
   }

   void printPackage(String packageName) {
      writer.println("package " + packageName + ";\n");
   }

   void printImports() {
      String imports = "import com.foreseeti.corelib.AssociationManager;\n" + "import com.foreseeti.corelib.BaseSample;\n" + "import com.foreseeti.corelib.DefaultValue;\n"
            + "import com.foreseeti.corelib.FAnnotations.Association;\n" + "import com.foreseeti.corelib.FAnnotations.Category;\n" + "import com.foreseeti.corelib.FAnnotations.Display;\n"
            + "import com.foreseeti.corelib.FAnnotations.DisplayClass;\n" + "import com.foreseeti.corelib.FAnnotations.TypeDescription;\n" + "import com.foreseeti.corelib.FAnnotations.TypeName;\n"
            + "import com.foreseeti.corelib.FClass;\n" + "import com.foreseeti.corelib.math.FMath;\n" + "import com.foreseeti.corelib.util.FProb;\n" + "import com.foreseeti.corelib.util.FProbSet;\n"
            + "import com.foreseeti.simulator.Asset;\n" + "import com.foreseeti.simulator.AttackStep;\n" + "import com.foreseeti.simulator.AttackStepMax;\n"
            + "import com.foreseeti.simulator.AttackStepMin;\n" + "import com.foreseeti.simulator.BaseLangLink;\n" + "import com.foreseeti.simulator.ConcreteSample;\n"
            + "import com.foreseeti.simulator.Constants;\n" + "import com.foreseeti.simulator.Defense;\n" + "import com.foreseeti.simulator.MultiParentAsset;\n"
            + "import com.google.common.collect.ImmutableSet;\n" + "import java.util.ArrayList;\n" + "import java.util.HashSet;\n" + "import java.util.List;\n" + "import java.util.Set;\n"
            + "import static org.junit.Assert.assertTrue;\n";
      writer.println(imports);
   }

   void printAssetClassHeaders(Asset asset) {
      String mandatory = "";
      if (!asset.getMandatoryChildren().isEmpty()) {
         mandatory += ", mandatoryChildren = {";
         String coma = "";
         for (Asset manAsset : asset.getMandatoryChildren()) {
            mandatory += coma + capitalize(manAsset.getName()) + ".class";
            coma = ",";
         }
         mandatory += "}";
      }

      String nonmandatory = "";
      if (!asset.getNonMandatoryChildren().isEmpty()) {
         nonmandatory += ", nonMandatoryChildren = {";
         String coma = "";
         for (Asset nonmanAsset : asset.getNonMandatoryChildren()) {
            nonmandatory += coma + capitalize(nonmanAsset.getName()) + ".class";
            coma = ",";
         }
         nonmandatory += "}";
      }

      String abs = "";
      if (asset.isAbstractAsset()) {
         abs = "abstract";
      }
      else {
         writer.print(String.format("@DisplayClass(category = Category.%s  %s  %s)\n", asset.getCategory(), mandatory, nonmandatory));
         writer.print(String.format("@TypeName(name = \"%s\")\n", asset.getName()));
      }

      writer.print(String.format("public %s class %s", abs, asset.getName()));
      if (asset.getSuperAssetName() != "") {
         writer.print(" extends " + asset.getSuperAssetName());
      }
      else {
         writer.print(" extends MultiParentAsset");
      }
      writer.println(" {");
   }

   void printAssociations(Asset asset) {
      int number = 1;
      for (Association association : model.getAssociations(asset)) {
         String association_annotations = String.format("@Association(index = %d, name = \"%s\")", number, association.getRightRoleName());
         writer.println(association_annotations);
         if (association.getRightMultiplicity().equals("*") || association.getRightMultiplicity().equals("1-*")) {
            writer.println("   public FProbSet<" + association.getRightAssetName() + "> " + association.getRightRoleName() + " = new FProbSet<>();");
         }
         else {
            if (association.getRightMultiplicity().equals("1") || association.getRightMultiplicity().equals("0-1")) {
               writer.println("   public FProb<" + association.getRightAssetName() + "> " + association.getRightRoleName() + ";");
            }
         }
         number++;
      }
      number = 1;
      writer.println("@Override");
      writer.println("public void registerAssociations() {");
      if (asset.getSuperAssetName() != null && !asset.getSuperAssetName().equals("")) {
         writer.println("super.registerAssociations();");
      }
      for (Association association : model.getAssociations(asset)) {
         String oppositeClass = association.getTargetAssetName(asset);
         String oppositeRole = association.getTargetRoleName(asset);
         String linkText = model.getLinks().get(association);
         if (association.getRightMultiplicity().equals("*")) {
            writer.println(
                  String.format("   AssociationManager.addSupportedAssociationMultiple(this.getClass(), \"%s\", %s.class, AutoLangLink.%s);", oppositeRole, capitalize(oppositeClass), linkText));
         }
         else if (association.getRightMultiplicity().equals("1-*")) {
            writer.println(String.format("   AssociationManager.addSupportedAssociationMultiple(this.getClass(), \"%s\", %s.class, 1, AssociationManager.NO_LIMIT, AutoLangLink.%s);", oppositeRole,
                  capitalize(oppositeClass), linkText));
         }
         else if (association.getRightMultiplicity().equals("0-1")) {
            writer.println(String.format("  AssociationManager.addSupportedAssociationSingle(this.getClass(), \"%s\", %s.class, AutoLangLink.%s);", oppositeRole, capitalize(oppositeClass), linkText));
         }
         else if (association.getRightMultiplicity().equals("1")) {
            writer.println(
                  String.format("  AssociationManager.addSupportedAssociationMandatorySingle(this.getClass(), \"%s\", %s.class, AutoLangLink.%s);", oppositeRole, capitalize(oppositeClass), linkText));
         }

         number++;
      }
      writer.println("}");

   }

   protected void createDefaultAttacker() {
      writer.println("package auto;");
      writer.println("import java.util.Set;");
      writer.println("import com.foreseeti.corelib.AssociationManager;");
      writer.println("import com.foreseeti.corelib.DefaultValue;");
      writer.println("import com.foreseeti.corelib.FAnnotations.Category;");
      writer.println("import com.foreseeti.corelib.FAnnotations.DisplayClass;");
      writer.println("import com.foreseeti.corelib.FAnnotations.TypeName;");
      writer.println("import com.foreseeti.corelib.FClass;");
      writer.println("import com.foreseeti.corelib.util.FProb;");
      writer.println("import com.foreseeti.corelib.util.FProbSet;");
      writer.println("import com.foreseeti.simulator.AbstractAttacker;");
      writer.println("import com.foreseeti.simulator.AttackStep;");
      writer.println("import com.foreseeti.simulator.BaseLangLink;");
      writer.println("import com.foreseeti.simulator.Defense;");
      writer.println("import com.google.common.collect.ImmutableSet;");

      writer.println("@DisplayClass(supportCapexOpex = false, category = Category.Attacker)");
      writer.println("@TypeName(name = \"Attacker\")");
      writer.println("public class Attacker extends AbstractAttacker {");

      writer.println("  public Attacker() {");
      writer.println("    this(DefaultValue.False);");
      writer.println("  }");

      writer.println("  public Attacker(DefaultValue val) {");
      writer.println("    firstSteps = new FProbSet<>();");
      writer.println("    fillElementMap();");
      writer.println("  }");

      writer.println("  public Attacker(Attacker other) {");
      writer.println("    super(other);");
      writer.println("    firstSteps = new FProbSet<>();");
      writer.println("    entryPoint = new EntryPoint(other.entryPoint);");
      writer.println("    fillElementMap();");
      writer.println("  }");

      writer.println("  @Override");
      writer.println("  public String getConnectionValidationErrors(String sourceFieldName, FClass target, String targetFieldName) {");
      writer.println("    if (Attacker.class.isAssignableFrom(target.getClass())) {");
      writer.println("      return \"Attacker can not be connected to other Attackers\";");
      writer.println("    }");
      writer.println("    return getConnectionValidationErrors(target.getClass());");
      writer.println("  }");

      writer.println("  @Override");
      writer.println("  public void registerAssociations() {");
      writer.println("    AssociationManager.addSupportedAssociationMultiple(this.getClass(),getName(1),AttackStep.class,0,AssociationManager.NO_LIMIT,BaseLangLink.Attacker_AttackStep);");

      writer.println("  }");

      writer.println("  @Override");
      writer.println("  public ImmutableSet<AttackStep> getAttackSteps() {");
      writer.println("    return ImmutableSet.copyOf(new AttackStep[] {entryPoint});");
      writer.println("  }");

      writer.println("  @Override");
      writer.println("  public ImmutableSet<Defense> getDefenses() {");
      writer.println("    return ImmutableSet.copyOf(new Defense[] {});");
      writer.println("  }");

      writer.println("  @TypeName(name = \"EntryPoint\")");
      writer.println("  public class EntryPoint extends AbstractAttacker.EntryPoint {");
      writer.println("    public EntryPoint() {}");

      writer.println("    public EntryPoint(AbstractAttacker.EntryPoint other) {");
      writer.println("      super(other);");
      writer.println("    }");

      writer.println("    @Override");
      writer.println("    public FClass getContainerFClass() {");
      writer.println("      return Attacker.this;");
      writer.println("    }");

      writer.println("    @Override");
      writer.println("    public java.util.Set<AttackStep> getAttackStepChildren() {");
      writer.println("      return FClass.toSampleSet(((Attacker) getContainerFClass()).firstSteps, null);");
      writer.println("    }");
      writer.println("  }");

      writer.println("  @Override");
      writer.println("  public boolean areAssociationsPublic() {");
      writer.println("    return false;");
      writer.println("  }");

      writer.println("  @Override");
      writer.println("  public boolean areModelElementsPublic() {");
      writer.println("    return false;");
      writer.println("  }");

      writer.println("  @Override");
      writer.println("  public boolean isAttacker() {");
      writer.println("    return true;");
      writer.println("  }");

      writer.println("}");

   }

   void printStepAssignments(Asset asset) {
      int num = 0;
      for (AttackStep attackStep : asset.getAttackSteps()) {
         if (!attackStep.getSuperAttackStepName().equals("") && !attackStep.isDefense()) {
            num++;
         }
      }
      for (AttackStep attackStep : asset.getAttackSteps()) {
         if (attackStep.getSuperAttackStepName().equals("")) {
            String display = "";
            boolean def = attackStep.isDefense();
            if ((!attackStep.isHiddenAttackStep() && !def) || (def && attackStep.isDisplayableDefense())) {
               display = "@Display";
            }
            if (!def) {
               writer.println(String.format("@Association(index = %d, name = \"%s\")", ++num, attackStep.getName()));
            }
            writer.print(String.format("%s   public %s %s ; \n", display, capitalize(attackStep.getName()), attackStep.getName()));
         }
      }
      writer.println("");
   }

   void printConstructor(Asset asset) {
      printDefaultConstructors(asset);
      printInitAttackStepsWithDefault(asset);
   }

   protected void printDefaultConstructors(Asset asset) {
      List<AttackStep> defensesExcludingExistenceRequirements = asset.defensesExcludingExistenceRequirements();
      String immutable_attacks = "";
      String immutable_defenses = "";
      String coma = "";
      if (defensesExcludingExistenceRequirements.isEmpty()) {
         writer.println(String.format("public %s(DefaultValue val) {this();}", capitalize(asset.getName())));
         writer.println(String.format("public %s(){initAttackStepsWithDefault();initAttackStepAndDefenseLists();}", capitalize(asset.getName())));
      }
      else {
         writer.println(String.format("public %s(){this(DefaultValue.False);}", capitalize(asset.getName())));
         writer.println(String.format("public %s(DefaultValue val) {", capitalize(asset.getName())));
         String booleanParams = "";
         writer.println("this(");
         for (AttackStep defense : defensesExcludingExistenceRequirements) {
            writer.print(String.format("%s val.get()", coma));
            booleanParams += String.format("%s boolean is%s", coma, capitalize(defense.getName()));
            coma = ",";
         }
         writer.println(");");
         writer.println("}");
         writer.println(String.format("public %s(%s){", capitalize(asset.getName()), booleanParams));
         for (AttackStep defense : defensesExcludingExistenceRequirements) {
            writer.println(String.format("%s = new %s(is%s);", defense.getName(), capitalize(defense.getName()), capitalize(defense.getName())));
         }
         for (AttackStep defense : asset.defensesWithExistenceRequirementsOnly()) {
            writer.println(String.format("%s = new %s(false);", defense.getName(), capitalize(defense.getName())));
         }
         writer.println("initAttackStepsWithDefault();");
         writer.println("initAttackStepAndDefenseLists();");
         writer.println("}");
      }
      writer.println(String.format("public %s(%s other) {", capitalize(asset.getName()), capitalize(asset.getName())));
      writer.println("super(other);");
      for (AttackStep defense : asset.defensesExcludingExistenceRequirements()) {
         writer.println(String.format("%s = new %s(other.%s);", defense.getName(), capitalize(defense.getName()), defense.getName()));
      }
      for (AttackStep attackStep : asset.attackStepsExceptDefensesAndExistence()) {
         writer.println(String.format("%s = new %s(other.%s);", attackStep.getName(), capitalize(attackStep.getName()), attackStep.getName()));
      }
      writer.println("initAttackStepAndDefenseLists();");
      writer.println("}");
      writer.println("");
      writer.println("private void initAttackStepAndDefenseLists() {");
      coma = "";
      for (AttackStep defense : asset.defenses()) {
         immutable_defenses += String.format("%s %s", coma, defense.getName());
         coma = ",";
      }
      writer.println(String.format("defenses = ImmutableSet.of(%s);", immutable_defenses));
      coma = "";
      for (AttackStep attackStep : asset.attackStepsExceptDefensesAndExistence()) {
         immutable_attacks += String.format("%s %s", coma, attackStep.getName());
         coma = ",";
      }
      for (AttackStep defense : asset.defenses()) {
         immutable_attacks += String.format("%s %s.disable", coma, defense.getName());
         coma = ",";
      }
      writer.println(String.format("attackSteps = ImmutableSet.of(%s);", immutable_attacks));
      writer.println("fillElementMap();\n");
      writer.println("}");
   }

   protected void printInitAttackStepsWithDefault(Asset asset) {
      writer.println("protected void initAttackStepsWithDefault() {");
      for (AttackStep attackStep : asset.attackStepsExceptDefensesAndExistence()) {
         writer.println(String.format("%s = new %s();", attackStep.getName(), capitalize(attackStep.getName())));
      }
      writer.println("}");
   }

   protected void printLocalAttackStepSpecialization(Asset asset) {
      writer.println("  public class LocalAttackStepMin extends AttackStepMin {");
      writer.println("    @Override");
      writer.println("    public FClass getContainerFClass() {");
      writer.println(String.format("      return %s.this;", capitalize(asset.getName())));
      writer.println("    }");
      writer.println("    LocalAttackStepMin() {}");
      writer.println("    LocalAttackStepMin(LocalAttackStepMin other) {");
      writer.println("      super(other);");
      writer.println("    }");
      writer.println("  }");

      writer.println("  public class LocalAttackStepMax extends AttackStepMax {");

      writer.println("    @Override");
      writer.println("    public FClass getContainerFClass() {");
      writer.println(String.format("      return %s.this;", capitalize(asset.getName())));
      writer.println("    }");

      writer.println("    LocalAttackStepMax() {}");

      writer.println("    LocalAttackStepMax(LocalAttackStepMax other) {");
      writer.println("      super(other);");
      writer.println("    }");
      writer.println("  }");
   }

   private String fileToBase64(File file) throws IOException {
      byte[] fileBytes = Files.readAllBytes(file.toPath());
      byte[] encodedBytes = Base64.getEncoder().encode(fileBytes);
      return new String(encodedBytes);
   }

   private String svgFileToPngBase64(File svgFile) throws TranscoderException {
      TranscoderInput svgInput = new TranscoderInput(svgFile.toPath().toString());
      ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
      TranscoderOutput pngOutput = new TranscoderOutput(byteOut);
      new PNGTranscoder().transcode(svgInput, pngOutput);
      byte[] pngBytes = byteOut.toByteArray();
      byte[] encodedBytes = Base64.getEncoder().encode(pngBytes);
      return new String(encodedBytes);
   }

   protected void printIcons(Asset asset) {
      // If there is no visualisation folder, don't print getIcon methods
      if (visualFolder == null) {
         return;
      }
      // Find files $assetName.{svg,png}
      String assetName = asset.getName();
      File svgFile = null;
      File pngFile = null;
      for (File file : visualFolder.listFiles()) {
         int dotIndex = file.getName().lastIndexOf('.');
         if (dotIndex == -1) {
            continue;
         }
         String fileName = file.getName().substring(0, dotIndex);
         if (!fileName.equals(assetName)) {
            continue;
         }
         String fileExtension = file.getName().substring(dotIndex + 1);
         if (fileExtension.equals("svg")) {
            svgFile = file;
         }
         else if (fileExtension.equals("png")) {
            pngFile = file;
         }
      }
      // If files couldn't be found, don't print getIcon methods
      if (svgFile == null && pngFile == null) {
         return;
      }
      // Get base64 encoded icons
      String svgBase64 = null;
      String pngBase64 = null;
      if (svgFile != null) {
         try {
            svgBase64 = fileToBase64(svgFile);
         }
         catch (IOException e) {
            e.printStackTrace();
         }
      }
      if (pngFile == null) {
         try {
            pngBase64 = svgFileToPngBase64(svgFile);
         }
         catch (TranscoderException e) {
            e.printStackTrace();
         }
      }
      else {
         try {
            pngBase64 = fileToBase64(svgFile);
         }
         catch (IOException e) {
            e.printStackTrace();
         }
      }
      if (svgBase64 != null) {
         writer.println("  public static String getIconSVG() {");
         writer.println("    return \"data:image/svg+xml;base64," + svgBase64 + "\";");
         writer.println("  }");
      }
      if (pngBase64 != null) {
         writer.println("  public static String getIconPNG() {");
         writer.println("    return \"data:image/png;base64," + pngBase64 + "\";");
         writer.println("  }");
         writer.println("  @Deprecated");
         writer.println("  public static String getIcon() {");
         writer.println("    return getIconPNG();");
         writer.println("  }");
      }
   }

   void printStepDefinitions(Asset asset) {
      for (AttackStep attackStep : asset.getAttackSteps()) {
         if (attackStep.isDefense()) {
            printDefenseDefinition(attackStep, asset);
         }
         else {
            printAttackStepDefinition(attackStep);
         }
      }
   }

   void printDefenseDefinition(AttackStep attackStep, Asset asset) {
      printDefenseSignature(attackStep);
      printDefenseConstructor(attackStep);
      printExistenceRequirements(attackStep);
      // A new Disable is created for each specialization, and then only the
      // most specialized is used. Not so pretty.
      printDisableDeclaration(attackStep, asset);
      printContainerFClassMethod(asset);
      writer.println("}\n");
   }

   protected void printContainerFClassMethod(Asset asset) {
      writer.println("@Override");
      writer.println("public FClass getContainerFClass() {");
      writer.println(String.format("return %s.this;", capitalize(asset.getName())));
      writer.println("}");
   }

   protected void printInfluenceDefense(AttackStep attackStep) {
      writer.println("@Override");
      writer.println("public Defense getInfluencingDefense() {");
      writer.println(String.format("return %s.this;", capitalize(attackStep.getName())));
      writer.println("}");
   }

   protected void printDisableDeclaration(AttackStep attackStep, Asset asset) {
      boolean max = attackStep.getAttackStepType().equals("&");
      writer.print("    public class Disable extends ");
      if (!attackStep.getSuperAttackStepName().equals("")) {
         writer.print(attackStep.getSuperAttackStepName() + ".Disable");
      }
      else {
         if (max) {
            writer.print("AttackStepMax");
         }
         else {
            writer.print("AttackStepMin");
         }

      }
      writer.println(" {");
      printUpdateChildren(attackStep);
      printContainerFClassMethod(asset);
      printInfluenceDefense(attackStep);
      writer.println("   }");
   }

   protected void printDefenseConstructor(AttackStep attackStep) {
      writer.println("    public " + capitalize(attackStep.getName()) + "(Boolean enabled) {");
      writer.println("      super(enabled);");
      writer.println("      disable = new Disable();");
      if (attackStep.ttcFunction.equals("BernoulliDistribution")) {
         writer.println(String.format("      setEvidenceDistribution(FMath.getBernoulliDist(%s));", attackStep.ttcParameters.get(0)));
      }
      writer.println("    }");
      AttackStep baseAttackStep = attackStep.getBaseAttackStep();
      if (baseAttackStep == null) {
         baseAttackStep = attackStep;
      }
      writer.println(String.format("    public %s(%s.%s other) {", capitalize(baseAttackStep.getName()), capitalize(baseAttackStep.getAsset().getName()), capitalize(baseAttackStep.getName())));
      writer.println("      super(other);");
      writer.println("      disable = new Disable();");
      if (attackStep.ttcFunction.equals("BernoulliDistribution")) {
         writer.println(String.format("      setEvidenceDistribution(FMath.getBernoulliDist(%s));", attackStep.ttcParameters.get(0)));
      }
      writer.println("    }\n");

   }

   protected void printDefenseSignature(AttackStep attackStep) {
      writer.println(String.format("@TypeName(name = \"%s\")", capitalize(attackStep.getName())));
      writer.println(String.format("@TypeDescription(text = \"%s\")", capitalize(attackStep.getName())));
      writer.print("   public class " + capitalize(attackStep.getName()) + " extends ");
      if (!attackStep.getSuperAttackStepName().equals("")) {
         writer.println(attackStep.getSuperAttackStepName() + " {");
      }
      else {
         writer.println("Defense {");
      }
   }

   void printExistenceRequirements(AttackStep attackStep) {
      if (!attackStep.getExistenceRequirementRoles().isEmpty()) {
         writer.println("   @Override");
         writer.println("   public boolean isEnabled(ConcreteSample sample) {");
         // The below should be the role name, not the asset name.
         // Furthermore, it should check for empty set rather than == null for
         // multiplicity associations
         Association association = model.getConnectedAssociation(attackStep.getAsset().getName(), attackStep.getExistenceRequirementRoles().get(0));
         assertTrue("Did not find the association from the asset " + attackStep.getAsset().getName() + " to the role " + attackStep.getExistenceRequirementRoles().get(0), association != null);
         String multiplicity = association.targetMultiplicityIncludingInheritance(attackStep.getAsset());
         if (multiplicity.equals("1") || multiplicity.equals("0-1")) {
            if (attackStep.getAttackStepType().equals("E")) {
               writer.println("      return " + attackStep.getExistenceRequirementRoles().get(0) + " == null;");
            }
            if (attackStep.getAttackStepType().equals("3")) {
               writer.println("      return " + attackStep.getExistenceRequirementRoles().get(0) + " != null;");
            }
         }
         else {
            if (attackStep.getAttackStepType().equals("E")) {
               writer.println("      return " + attackStep.getExistenceRequirementRoles().get(0) + ".isEmpty();");
            }
            if (attackStep.getAttackStepType().equals("3")) {
               writer.println("      return !" + attackStep.getExistenceRequirementRoles().get(0) + ".isEmpty();");
            }
         }
         writer.println("   }");
      }

   }

   void printAttackStepDefinition(AttackStep attackStep) {
      writer.print("  public class " + capitalize(attackStep.getName()) + " extends ");
      String attackStepTypeString = "";
      if (!attackStep.getSuperAttackStepName().equals("")) {
         attackStepTypeString = attackStep.getSuperAttackStepName();
      }
      else {
         if (attackStep.getAttackStepType().equals("&")) {
            attackStepTypeString = "LocalAttackStepMax";
         }
         if (attackStep.getAttackStepType().equals("|")) {
            attackStepTypeString = "LocalAttackStepMin";
         }
         if (attackStep.getAttackStepType().equals("t")) {
            attackStepTypeString = "CPT_AttackStep";
         }
      }
      AttackStep baseAttackStep = attackStep.getBaseAttackStep();
      if (baseAttackStep == null) {
         baseAttackStep = attackStep;
      }
      assert (!attackStepTypeString.equals(""));

      writer.println(attackStepTypeString + " {");
      writer.println(String.format("    public %s(%s.%s other) {", capitalize(attackStep.getName()), capitalize(baseAttackStep.getAsset().getName()), capitalize(baseAttackStep.getName())));
      writer.println("      super(other);");
      writer.println("    }");
      writer.println(String.format("    public %s() {", capitalize(attackStep.getName())));
      writer.println("    }");
      printSetExpectedParents(attackStep);
      printUpdateChildren(attackStep);
      printLocalTtc(attackStep);

      writer.println("  }\n");
   }

   private void printLocalTtc(AttackStep attackStep) {
      if (!attackStep.ttcFunction.equals("Default")) {
         writer.println("    @Override");
         writer.println("    public double defaultLocalTtc(BaseSample sample, AttackStep caller) {");

         switch (attackStep.ttcFunction) {
            case "Zero":
               writer.println("      return Constants.oneSecond;");
               break;
            case "BernoulliDistribution":
               writer.println(String.format("      if (FMath.getBernoulliDist(%f).sample()) {", attackStep.ttcParameters.get(0)));
               writer.println("        return Constants.oneSecond;");
               writer.println("      } else {");
               writer.println("        return Constants.infinity;");
               writer.println("      }");
               break;
            case "TruncatedNormalDistribution":
               writer.println(String.format("      return FMath.getTruncatedNormalDist(%f, %f).sample();", attackStep.ttcParameters.get(0), Math.sqrt(attackStep.ttcParameters.get(1))));
               break;
            case "ExponentialDistribution":
               writer.println(String.format("      return FMath.getExponentialDist(%f).sample();", 1.0 / attackStep.ttcParameters.get(0)));
               break;
            case "GammaDistribution":
               writer.println(String.format("      return FMath.getGammaDist(%f, %f).sample();", attackStep.ttcParameters.get(0), 1.0 / attackStep.ttcParameters.get(1)));
               break;
            case "LogNormalDistribution":
               writer.println(String.format("      return FMath.getLogNormalDist(%f, %f).sample();", Math.sqrt(attackStep.ttcParameters.get(1)), Math.log(attackStep.ttcParameters.get(0))));
               break;
            case "ParetoDistribution":
               writer.println(String.format("      return FMath.getParetoDist(%f, %f).sample();", attackStep.ttcParameters.get(0), attackStep.ttcParameters.get(1)));
               break;
            case "Infinity":
               writer.println("      return Constants.infinity;");
               break;
            default:
               System.err.println(String.format("Error: Unsupported distribution \"%s\" for attack step %s.%s", attackStep.ttcFunction, attackStep.getAsset().name, attackStep.name));
               System.err.println("       Using \"Constants.oneSecond\" instead");
               writer.println("      return Constants.oneSecond;");
         }
         writer.println("    }");
      }
   }

   void printUpdateChildren(AttackStep attackStep) {
      if (!attackStep.steps.isEmpty()) {

         writer.println("@Override");
         writer.println("public java.util.Set<AttackStep> getAttackStepChildren() {");
         if (!attackStep.isSpecialization() || attackStep.isExtension) {
            writer.println("java.util.Set<AttackStep> set = new java.util.HashSet<>(super.getAttackStepChildren());");
         }
         else {
            writer.println("java.util.Set<AttackStep> set = new java.util.HashSet<>();");
         }
         for (Step step : attackStep.steps) {
            step.print(writer, "set.add(%s);\n", "(null)");
         }
         writer.println("return set;");
         writer.println("}");
      }
   }

   void printSetExpectedParents(AttackStep attackStep) {
      if (!attackStep.parentSteps.isEmpty()) {
         writer.println("@Override");
         writer.println("protected void setExpectedParents(ConcreteSample sample) {");
         if (!attackStep.getSuperAttackStepName().isEmpty()) {
            writer.println("super.setExpectedParents(sample);");
         }
         if (!attackStep.getExistenceRequirementRoles().isEmpty()) {
            writer.println(String.format("if (%s != null) {", attackStep.getExistenceRequirementRoles().get(0)));
         }
         for (Step step : attackStep.parentSteps) {
            if (model.getAsset(step.getTargetAsset()).getAttackStep(step.to).isDefense()) {
               step.print(writer, "sample.addExpectedParent(this, %s.disable);\n", "(sample)");
            }
            else {
               step.print(writer, "sample.addExpectedParent(this, %s);\n", "(sample)");
            }
         }
         if (!attackStep.getExistenceRequirementRoles().isEmpty()) {
            writer.println("}");
         }
         writer.println("}");
      }
   }

   void printConnectionHelpers(Asset asset) {

      for (Association association : asset.getAssociations()) {

         if (association.getRightMultiplicity().equals("*") || association.getRightMultiplicity().equals("1-*")) {
            writer.println(String.format("public java.util.Set<%s> %s(BaseSample sample) { return toSampleSet(%s, sample); } \n", capitalize(association.getRightAssetName()),
                  association.getRightRoleName(), association.getRightRoleName()));
         }
         else {
            writer.println(String.format("public %s %s(BaseSample sample) { return toSample(%s, sample); } \n", capitalize(association.getRightAssetName()), association.getRightRoleName(),
                  association.getRightRoleName()));
         }
      }
   }

}
