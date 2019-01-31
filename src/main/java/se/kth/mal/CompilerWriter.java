package se.kth.mal;

import static org.junit.Assert.assertTrue;

//Iff any attack step (internal or external) points to an inherited attack step in an asset:
//Create a specialization of that attack step, in the specialized asset.
//Override the setExpectedParents() method, including the pointing attack step as parent in the specialization.
//(In the generalized attack step, the pointing attack step should not be included).
//Don't instantiate the attack step in the generalized class but instead in the constructor.

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import se.kth.mal.steps.Step;

// After changing sLang.g4, in bash, in
// /Users/pontus/Documents/Pontus\ Program\ Files/Eclipse/securiLangDSL2d3js/src, run
// "antlr4 sLang.g4"

// The JavaWriter produces executable Java code for testing purposes.
public class CompilerWriter {

   PrintWriter          writer;
   String               securiLangFolder;
   String               securiLangFile;
   String               testCasesFolder;
   String               jsonString = "";
   public CompilerModel model;

   Integer              associationIndex;

   private String package2path(String packageName) throws IllegalArgumentException {
      if (!packageName.matches("\\w+(\\.\\w+)*$")) {
         throw (new IllegalArgumentException("'" + packageName + "' is not a valid package name"));
      }

      return packageName.replace('.', File.separatorChar);
   }

   public CompilerWriter(String securiLangFolder, String securiLangFile, String testCasesFolder, String javaFolder, String packageName, String jsonFolder)
         throws IOException, IllegalArgumentException {
      this.securiLangFolder = securiLangFolder;
      this.securiLangFile = securiLangFile;
      this.testCasesFolder = testCasesFolder;

      String packagePath = package2path(packageName);
      model = new CompilerModel(securiLangFolder, securiLangFile);
      writeD3(jsonFolder, securiLangFile);
      writeJava(javaFolder, packageName, packagePath);
      PrintTTCFileTemplate.writeTTCConfigFile(jsonFolder + "/attackerProfile.ttc", securiLangFolder, securiLangFile);
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

   public void writeD3(String outputFolder, String outputFileName) {

      // Create the path unless it already exists
      String path = outputFolder + "/";
      (new File(path)).mkdirs();

      String ofn = outputFileName.substring(0, outputFileName.lastIndexOf('.'));
      String outputFile = outputFolder + "/" + ofn + ".json";
      try {
         writer = new PrintWriter(outputFile, "UTF-8");
         writeToJsonStringln("{");
         writeToJsonStringln(" \"name\": \"securiLang\",");
         writeToJsonStringln(" \"children\": [");
         for (Asset asset : model.getAssets()) {

            writeToJsonStringln("  {");
            writeToJsonStringln("   \"name\": \"" + asset.name + "\",");
            if (asset.attackSteps.size() > 0) {
               writeToJsonStringln("   \"children\": [");
               for (AttackStep attackStep : asset.attackSteps) {
                  writeToJsonStringln("    {");
                  writeToJsonStringln("     \"name\": \"" + attackStep.name + "\",");
                  if (attackStep.attackStepType.equals("#") || attackStep.attackStepType.equals("3") || attackStep.attackStepType.equals("E")) {
                     writeToJsonStringln("     \"type\": \"defense\",");
                  }
                  else {
                     if (attackStep.attackStepType.equals("&")) {
                        writeToJsonStringln("     \"type\": \"and\",");
                     }
                     else {
                        writeToJsonStringln("     \"type\": \"or\",");
                     }
                  }
                  AttackStep superAttackStep = attackStep.getSuper();
                  if (!attackStep.steps.isEmpty() || superAttackStep != null) {
                     writeToJsonStringln("\"targets\": [");
                     for (Step step : attackStep.steps) {
                        writeToJsonStringln(String.format("{\"name\": \"%s\", \"entity_name\": \"%s\", \"size\": 4000}", step.to, step.getTargetAsset()));
                     }
                     if (superAttackStep != null) {
                        writeToJsonStringln(String.format("{\"name\": \"%s\", \"entity_name\": \"%s\", \"size\": 4000}", superAttackStep.name, superAttackStep.asset.name));
                     }
                     writeToJsonStringln("],");
                  }
                  backtrackJsonString();
                  writeToJsonStringln("    },");
               }
               backtrackJsonString();
               writeToJsonStringln("   ],");
            }
            backtrackJsonString();
            writeToJsonStringln("  },");
         }
         backtrackJsonString();
         writeToJsonStringln(" ],");
         backtrackJsonString();
         writeToJsonStringln("}");
         writer.println(jsonString);
         writer.close();
      }
      catch (FileNotFoundException e) {
         System.out.println("FileNotFoundException");
         e.printStackTrace();
      }
      catch (UnsupportedEncodingException e) {
         System.out.println("UnsupportedEncodingException");
         e.printStackTrace();
      }
   }

   private void writeJava(String outputFolder, String packageName, String packagePath) {

      // Create the path unless it already exists
      String path = outputFolder + "/" + packagePath + "/";
      (new File(path)).mkdirs();

      for (Asset asset : model.getAssets()) {
         System.out.println("Writing the Java class corresponding to asset " + asset.name);
         String sourceCodeFile = path + asset.name + ".java";
         try {
            writer = new PrintWriter(sourceCodeFile, "UTF-8");
            printPackage(packageName);
            printImports();
            printAssetClassHeaders(asset);
            printAssociations(asset);
            printStepAssignments(asset);
            printConstructor(asset);
            printStepDefinitions(asset);
            printConnectionHelpers(asset);
            printGetAssociatedAssetClassName(asset);
            printGetAssociatedAssets(asset);
            printGetAllAssociatedAssets(asset);
            writer.println("}");
            writer.close();

         }
         catch (FileNotFoundException e) {
            System.out.println("FileNotFoundException");
            e.printStackTrace();
         }
         catch (UnsupportedEncodingException e) {
            System.out.println("UnsupportedEncodingException");
            e.printStackTrace();
         }
      }
   }

   void printPackage(String packageName) {
      writer.println("package " + packageName + ";\n");
   }

   void printImports() {
      String imports = "import java.util.ArrayList;\nimport java.util.HashSet;\nimport java.util.List;\nimport java.util.Set;\nimport static org.junit.Assert.assertTrue;\n\nimport core.Asset;\nimport core.AttackStep;\nimport core.AttackStepMax;\nimport core.AttackStepMin;\nimport core.Defense;";
      writer.println(imports);
   }

   void printAssetClassHeaders(Asset asset) {
      writer.print("public class " + asset.name);
      if (asset.superAssetName != "") {
         writer.print(" extends " + asset.superAssetName);
      }
      else {
         writer.print(" extends Asset");
      }
      writer.println(" {");
   }

   void printAssociations(Asset asset) {
      for (Association association : model.getAssociations(asset)) {
         if (association.rightMultiplicity.equals("*") || association.rightMultiplicity.equals("1-*")) {
            writer.println("   public Set<" + association.rightAssetName + "> " + association.rightRoleName + " = new HashSet<>();");
         }
         else {
            if (association.rightMultiplicity.equals("1") || association.rightMultiplicity.equals("0-1")) {
               writer.println("   public " + association.rightAssetName + " " + association.rightRoleName + ";");
            }
         }
      }
      writer.println("");
   }

   void printStepAssignments(Asset asset) {
      for (AttackStep attackStep : asset.attackSteps) {
         if (attackStep.superAttackStepName.equals("")) {
            writer.print("   public " + capitalize(attackStep.name) + " " + attackStep.name + ";\n");
         }
      }
      writer.println("");
   }

   void printConstructor(Asset asset) {
      String constructorString = "";
      constructorString = sprintConstructorWithDefenseAttributes(asset, false, constructorString);
      constructorString = sprintConstructorWithDefenseAttributes(asset, true, constructorString);
      constructorString = sprintConstructorWithoutDefenseAttributes(asset, false, constructorString);
      constructorString = sprintConstructorWithoutDefenseAttributes(asset, true, constructorString);
      writer.println(constructorString);
   }

   protected String sprintConstructorWithoutDefenseAttributes(Asset asset, boolean hasName, String constructorString) {
      if (!asset.defensesExcludingExistenceRequirements().isEmpty()) {
         constructorString += "   public " + capitalize(asset.name) + "(";
         if (hasName) {
            constructorString += "String name";
         }
         constructorString += ") {\n";
         constructorString += "      this(";
         if (hasName) {
            constructorString += "name, ";
         }

         for (AttackStep defense : asset.defensesExcludingExistenceRequirements()) {
            constructorString += "false, ";
         }
         constructorString = constructorString.substring(0, constructorString.length() - 2);
         constructorString += ");\n";
         constructorString += "      assetClassName = \"" + asset.name + "\";\n   }\n\n";
      }
      return constructorString;
   }

   protected String sprintConstructorWithDefenseAttributes(Asset asset, boolean hasName, String constructorString) {
      constructorString += "   public " + capitalize(asset.name) + "(";
      if (hasName) {
         constructorString += "String name";
         if (!asset.defensesExcludingExistenceRequirements().isEmpty()) {
            constructorString += ", ";
         }
      }
      constructorString = sprintDefenseAttributes(asset, constructorString);
      constructorString += ") {\n";
      constructorString = sprintSuperCall(asset, hasName, constructorString);
      constructorString = sprintStepCreation(asset, constructorString);
      constructorString += "      assetClassName = \"" + asset.name + "\";\n   }\n\n";
      return constructorString;
   }

   protected String sprintSuperCall(Asset asset, boolean hasName, String constructorString) {
      constructorString += "      super(";
      Asset superAsset = null;
      if (!asset.superAssetName.equals("")) {
         superAsset = model.getAsset(asset.superAssetName);
      }
      if (hasName) {
         constructorString += "name";
         if (!asset.superAssetName.equals("")) {
            if (!superAsset.defensesExcludingExistenceRequirements().isEmpty()) {
               constructorString += ", ";
            }
         }
      }
      if (!asset.superAssetName.equals("")) {
         for (AttackStep defense : superAsset.defensesExcludingExistenceRequirements()) {
            constructorString += defense.name + "State, ";
         }
         if (!superAsset.defensesExcludingExistenceRequirements().isEmpty()) {
            constructorString = constructorString.substring(0, constructorString.length() - 2);
         }
      }

      constructorString += ");\n";
      return constructorString;
   }

   protected String sprintDefenseAttributes(Asset asset, String constructorString) {
      String attributesString = "";
      if (!asset.defensesExcludingExistenceRequirements().isEmpty()) {
         for (AttackStep defense : asset.defensesExcludingExistenceRequirements()) {
            attributesString += "Boolean " + defense.name + "State, ";
         }
         if (attributesString.length() > 0) {
            attributesString = attributesString.substring(0, attributesString.length() - 2);
         }
      }
      return constructorString + attributesString;
   }

   protected String sprintStepCreation(Asset asset, String constructorString) {
      for (AttackStep defense : asset.defenses()) {
         constructorString += "      if (" + defense.name + " != null) {\n";
         constructorString += "         AttackStep.allAttackSteps.remove(" + defense.name + ".disable);\n";
         constructorString += "      }\n";
         constructorString += "      Defense.allDefenses.remove(" + defense.name + ");\n";
         constructorString += "      " + defense.name + " = new " + capitalize(defense.name) + "(this.name";
         if (!defense.hasExistenceRequirements()) {
            constructorString += ", " + defense.name + "State";
         }
         constructorString += ");\n";
      }
      for (AttackStep attackStep : asset.attackSteps) {
         if (!asset.defenses().contains(attackStep)) {
            constructorString += "      AttackStep.allAttackSteps.remove(" + attackStep.name + ");\n";
            constructorString += "      " + attackStep.name + " = new " + capitalize(attackStep.name) + "(this.name);\n";
         }
      }
      return constructorString;
   }

   void printStepDefinitions(Asset asset) {
      for (AttackStep attackStep : asset.attackSteps) {
         if (attackStep.attackStepType.equals("#") || attackStep.attackStepType.equals("E") || attackStep.attackStepType.equals("3")) {
            printDefenseDefinition(attackStep);
         }
         else {
            printAttackStepDefinition(attackStep);
         }
      }
   }

   void printDefenseDefinition(AttackStep attackStep) {
      printDefenseSignature(attackStep);
      printDefenseConstructor(attackStep);
      printExistenceRequirements(attackStep);
      // A new Disable is created for each specialization, and then only the
      // most specialized is used. Not so pretty.
      printDisableDeclaration(attackStep);
      writer.println("}\n");
   }

   protected void printDisableDeclaration(AttackStep attackStep) {
      writer.print("   public class Disable extends ");
      if (!attackStep.superAttackStepName.equals("")) {
         writer.print(attackStep.superAttackStepName + ".Disable");
      }
      else {
         writer.print("AttackStepMin");
      }
      writer.println(" {");
      // writer.println(" String defenseName = \"" + attackStep.name + "\";");
      writer.println("         public Disable(String name) {");
      writer.println("            super(name);");
      writer.println("         }\n");
      writer.println("         @Override");
      writer.println("         public String fullName() {");
      writer.println("            return \"" + attackStep.fullDefaultName() + "\";");
      writer.println("         }");
      printUpdateChildren(attackStep);
      writer.println("   }");
   }

   protected void printDefenseConstructor(AttackStep attackStep) {
      writer.print("   public " + capitalize(attackStep.name) + "(String name");
      if (!attackStep.hasExistenceRequirements()) {
         writer.print(", Boolean enabled");
      }
      writer.println(") {");
      writer.print("      super(name");
      if (!attackStep.superAttackStepName.equals("") && !attackStep.hasExistenceRequirements()) {
         writer.print(", enabled");
      }
      writer.println(");");
      if (attackStep.superAttackStepName.equals("")) {

         if (!attackStep.hasExistenceRequirements()) {
            writer.println("      defaultValue = enabled;");
         }
      }
      writer.println("      disable = new Disable(name);");
      writer.println("   }\n");
   }

   protected void printDefenseSignature(AttackStep attackStep) {
      writer.print("   public class " + capitalize(attackStep.name) + " extends ");
      if (!attackStep.superAttackStepName.equals("")) {
         writer.println(attackStep.superAttackStepName + " {");
      }
      else {
         writer.println("Defense {");
      }
   }

   void printExistenceRequirements(AttackStep attackStep) {
      if (!attackStep.existenceRequirementRoles.isEmpty()) {
         writer.println("   @Override");
         writer.println("   public boolean isEnabled() {");
         // The below should be the role name, not the asset name.
         // Furthermore, it should check for empty set rather than == null for
         // multiplicity associations
         Association association = model.getConnectedAssociation(attackStep.asset.name, attackStep.existenceRequirementRoles.get(0));
         assertTrue("Did not find the association from the asset " + attackStep.asset.name + " to the role " + attackStep.existenceRequirementRoles.get(0), association != null);
         String multiplicity = association.targetMultiplicityIncludingInheritance(attackStep.asset);
         if (multiplicity.equals("1") || multiplicity.equals("0-1")) {
            if (attackStep.attackStepType.equals("E")) {
               writer.println("      return " + attackStep.existenceRequirementRoles.get(0) + " == null;");
            }
            if (attackStep.attackStepType.equals("3")) {
               writer.println("      return " + attackStep.existenceRequirementRoles.get(0) + " != null;");
            }
         }
         else {
            if (attackStep.attackStepType.equals("E")) {
               writer.println("      return " + attackStep.existenceRequirementRoles.get(0) + ".isEmpty();");
            }
            if (attackStep.attackStepType.equals("3")) {
               writer.println("      return !" + attackStep.existenceRequirementRoles.get(0) + ".isEmpty();");
            }
         }
         writer.println("   }");
      }

   }

   void printAttackStepDefinition(AttackStep attackStep) {
      writer.print("   public class " + capitalize(attackStep.name) + " extends ");
      String attackStepTypeString = "";
      if (!attackStep.superAttackStepName.equals("")) {
         attackStepTypeString = attackStep.superAttackStepName;
      }
      else {
         if (attackStep.attackStepType.equals("&")) {
            attackStepTypeString = "AttackStepMax";
         }
         if (attackStep.attackStepType.equals("|")) {
            attackStepTypeString = "AttackStepMin";
         }
         if (attackStep.attackStepType.equals("t")) {
            attackStepTypeString = "CPT_AttackStep";
         }
      }
      assert (!attackStepTypeString.equals(""));
      writer.println(attackStepTypeString + " {");
      writer.println("   public " + capitalize(attackStep.name) + "(String name) {");
      writer.println("      super(name);");
      writer.println("      assetClassName = \"" + attackStep.asset.name + "\";");
      writer.println("   }");

      printSetExpectedParents(attackStep);
      printUpdateChildren(attackStep);
      printLocalTtc(attackStep);

      writer.println("   }\n");
   }

   void printUpdateChildren(AttackStep attackStep) {
      if (!attackStep.steps.isEmpty()) {
         writer.println("@Override");
         writer.println("public void updateChildren(Set<AttackStep> activeAttackSteps) {");
         for (Step step : attackStep.steps) {
            step.print(writer, "%s.updateTtc(this, ttc, activeAttackSteps);\n");
         }
         writer.println("}");
      }
   }

   void printSetExpectedParents(AttackStep attackStep) {
      if (!attackStep.parentSteps.isEmpty()) {
         writer.println("@Override");
         writer.println("public void setExpectedParents() {");
         if (!attackStep.getSuperAttackStepName().isEmpty()) {
            writer.println("super.setExpectedParents();");
         }
         if (!attackStep.getExistenceRequirementRoles().isEmpty()) {
            writer.println(String.format("if (%s != null) {", attackStep.getExistenceRequirementRoles().get(0)));
         }
         for (Step step : attackStep.parentSteps) {
            if (model.getAsset(step.getTargetAsset()).getAttackStep(step.to).isDefense()) {
               step.print(writer, "addExpectedParent(%s.disable);\n");
            }
            else {
               step.print(writer, "addExpectedParent(%s);\n");
            }
         }
         if (!attackStep.getExistenceRequirementRoles().isEmpty()) {
            writer.println("}");
         }
         writer.println("}");
      }
   }

   void printLocalTtc(AttackStep attackStep) {
      if (attackStep.asset.superAssetName.equals("") || !attackStep.ttcFunction.equals("Default")) {
         writer.println("      @Override");
         writer.println("      public double localTtc() {");
         writer.println("         return ttcHashMap.get(\"" + attackStep.asset.name + "." + attackStep.name + "\");");
         // if (attackStep.ttcFunction.equals("ExponentialDistribution")) {
         // writer.println(" return " +
         // attackStep.ttcParameters.get(0).toString() + ";");
         // }
         // else {
         // if (attackStep.ttcFunction.equals("GammaDistribution")) {
         // writer.println(" return " +
         // Float.toString((attackStep.ttcParameters.get(0) *
         // attackStep.ttcParameters.get(1))) + ";");
         // }
         // else {
         // if (attackStep.ttcFunction.equals("BernoulliDistribution")) {
         // writer.println(" if (" + attackStep.ttcParameters.get(0).toString()
         // + " > 0.5 ) {");
         // writer.println(" return oneSecond;\n}");
         // writer.println(" else {");
         // writer.println(" return infinity;\n}");
         // }
         // else {
         // assert false : "Unknown distribution for attack step " +
         // attackStep.asset.name + "." + attackStep.name + ".";
         // }
         // }
         // }
         writer.println("      }\n");
      }
   }

   void printConnectionHelpers(Asset asset) {
      for (Association association : asset.getAssociations()) {
         String targetAssetName = association.getTargetAssetName(asset);
         String targetRoleName = association.getTargetRoleName(asset);
         String sourceRoleName = association.getSourceRoleName(asset);
         writer.println("      public void add" + capitalize(targetRoleName) + "(" + targetAssetName + " " + targetRoleName + ") {");
         if (association.targetMultiplicity(asset).equals("0-1") || association.targetMultiplicity(asset).equals("1")) {
            writer.println("         this." + targetRoleName + " = " + targetRoleName + ";");
         }
         else {
            writer.println("         this." + targetRoleName + ".add(" + targetRoleName + ");");
         }
         if (association.sourceMultiplicity(asset).equals("0-1") || association.sourceMultiplicity(asset).equals("1")) {
            writer.println("         " + targetRoleName + "." + sourceRoleName + " = this;");
         }
         else {
            writer.println("         " + targetRoleName + "." + sourceRoleName + ".add(this);");
         }
         writer.println("      }\n");
      }
   }

   void printGetAssociatedAssetClassName(Asset asset) {
      writer.println("   @Override");
      writer.println("   public String getAssociatedAssetClassName(String roleName) {");
      for (Association association : asset.getAssociations()) {
         writer.println("      if (roleName.equals(\"" + association.getTargetRoleName(asset) + "\")) {");
         if (association.targetMultiplicity(asset).equals("*") || association.targetMultiplicity(asset).equals("1-*")) {
            writer.println("         for (Object o: " + association.getTargetRoleName(asset) + ") {");
            writer.println("            return o.getClass().getName();");
            writer.println("         }");
         }
         else {
            writer.println("         return " + association.getTargetRoleName(asset) + ".getClass().getName();");
         }
         writer.println("      }");
      }
      writer.println("      return null;");
      writer.println("   }");
   }

   void printGetAssociatedAssets(Asset asset) {
      writer.println("   @Override");
      writer.println("   public Set<Asset> getAssociatedAssets(String roleName) {");
      writer.println("      Set<Asset> assets = new HashSet<>();");
      for (Association association : asset.getAssociationsIncludingInherited()) {
         writer.println(
               "      if (roleName.equals(\"" + association.getTargetRoleNameIncludingInheritance(asset) + "\")  && " + association.getTargetRoleNameIncludingInheritance(asset) + " != null) {");
         if (association.targetMultiplicityIncludingInheritance(asset).equals("*") || association.targetMultiplicityIncludingInheritance(asset).equals("1-*")) {
            writer.println("         assets.addAll(" + association.getTargetRoleNameIncludingInheritance(asset) + ");");
         }
         else {
            writer.println("         assets.add(" + association.getTargetRoleNameIncludingInheritance(asset) + ");");
         }
         writer.println("         return assets;");
         writer.println("      }");
      }
      writer.println("      assertTrue(\"The asset \" + this.toString() + \" does not feature the role name \" + roleName + \".\", false);");
      writer.println("      return null;");
      writer.println("   }");
   }

   void printGetAllAssociatedAssets(Asset asset) {
      writer.println("   @Override");
      writer.println("   public Set<Asset> getAllAssociatedAssets() {");
      writer.println("      Set<Asset> assets = new HashSet<>();");
      for (Association association : asset.getAssociationsIncludingInherited()) {
         if (association.targetMultiplicityIncludingInheritance(asset).equals("*") || association.targetMultiplicityIncludingInheritance(asset).equals("1-*")) {
            writer.println("      assets.addAll(" + association.getTargetRoleNameIncludingInheritance(asset) + ");");
         }
         else {
            writer.println("      if (" + association.getTargetRoleNameIncludingInheritance(asset) + " != null) {");
            writer.println("         assets.add(" + association.getTargetRoleNameIncludingInheritance(asset) + ");");
            writer.println("      }");
         }
      }
      writer.println("      return assets;");
      writer.println("   }");
   }

   private void writeToJsonStringln(String s) {
      jsonString = jsonString + s + "\n";
   }

   private void backtrackJsonString() {
      jsonString = jsonString.substring(0, jsonString.length() - 2) + "\n";
   }

   public Set<String> listFilesForFolder(final File folder) {
      Set<String> testClassNames = new HashSet<>();
      for (final File fileEntry : folder.listFiles()) {
         if (fileEntry.isDirectory()) {
            listFilesForFolder(fileEntry);
         }
         else {
            if (fileEntry.getName().indexOf(".csv") != -1) {
               testClassNames.add(fileEntry.getName().split("\\.")[0]);
            }
         }
      }
      return testClassNames;
   }

   private void writeTestCases(String securiLangFolderPath) {

      final File folder = new File(securiLangFolderPath + "/testCases/");
      Set<String> testClassNames = listFilesForFolder(folder);
      for (String testClassName : testClassNames) {
         Set<TestingCase> testingCases = new HashSet<>();
         readTestCaseFile(securiLangFolderPath, testClassName, testingCases);
         writeTests(securiLangFolderPath, testClassName, testingCases);
      }
      writeTestRunner(securiLangFolderPath);
   }

   protected void readTestCaseFile(String securiLangFolderPath, String fileName, Set<TestingCase> testingCases) {
      try {
         String testFile = readFile(securiLangFolderPath + "/testCases/" + fileName + ".csv");
         String[] rows = testFile.split("\n");
         int nRows = rows.length;
         int nCols = rows[0].split(";").length;
         String[][] matrix = new String[nRows][nCols];
         for (int iRow = 0; iRow < nRows; iRow++) {
            String[] words = rows[iRow].split(";");
            for (int iCol = 0; iCol < nCols; iCol++) {
               matrix[iRow][iCol] = words[iCol];
            }
            List<String> newValues = new ArrayList<>();
            for (int iWord = 2; iWord < words.length; iWord++) {
               newValues.add(words[iWord]);
            }
         }

         for (int iCol = 3; iCol < nCols; iCol++) {
            TestingCase testingCase = new TestingCase();
            for (int iRow = 0; iRow < nRows; iRow++) {
               AttackStepName attackPoint = new AttackStepName();
               attackPoint.assetName = matrix[iRow][1].toLowerCase();
               testingCase.assetNames.add(matrix[iRow][1]);
               attackPoint.attackStepName = matrix[iRow][2];
               if (matrix[iRow][0].equals("attackStep")) {
                  if (matrix[iRow][iCol].equals("entryPoint")) {
                     testingCase.attackPointNames.add(attackPoint);
                  }
                  if (matrix[iRow][iCol].equals("infinity")) {
                     testingCase.attackStepStates.put(attackPoint, " == AttackStep.infinity");
                  }
                  if (matrix[iRow][iCol].equals("lessThanADay")) {
                     testingCase.attackStepStates.put(attackPoint, " < 1");
                  }
               }
               if (matrix[iRow][0].equals("defenseStep")) {
                  testingCase.defenseStepStates.put(attackPoint, matrix[iRow][iCol]);
               }
            }
            testingCases.add(testingCase);
         }
      }
      catch (IOException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
   }

   protected void writeTests(String javaFolderPath, String testCaseName, Set<TestingCase> testingCases) {

      // Doing it wrong:

      String testCodeFile = javaFolderPath + "/autoLang/test/test" + testCaseName + ".java";
      try {
         writer = new PrintWriter(testCodeFile, "UTF-8");
         writer.println("import static org.junit.Assert.assertTrue;");

         writer.println("import org.junit.Test;");
         writer.println("");
         writer.println("public class test" + testCaseName + " {");
         writer.println("");
         Integer iCase = 0;
         for (TestingCase testingCase : testingCases) {
            iCase++;
            writer.println("   @Test");
            writer.println("   public void test" + iCase.toString() + "() {");
            for (String assetName : testingCase.assetNames) {
               writer.println("      " + assetName + " " + assetName.toLowerCase() + " = new " + assetName + "();");
            }
            for (Map.Entry<AttackStepName, String> entry : testingCase.defenseStepStates.entrySet()) {
               AttackStepName defenseStepName = entry.getKey();
               String defenseStepState = entry.getValue();
               writer.println("      " + defenseStepName.assetName.toLowerCase() + "." + defenseStepName.attackStepName + ".defaultValue = " + defenseStepState + ";");
            }
            writer.println("");
            writer.println("      Attacker attacker = new Attacker();");
            for (AttackStepName attackPointName : testingCase.attackPointNames) {
               writer.println("      attacker.addAttackPoint(" + attackPointName.assetName.toLowerCase() + "." + attackPointName.attackStepName + ");");
            }
            writer.println("      attacker.attack();");
            writer.println("");
            // writer.println(" Support.explain(" + asset.name.toLowerCase() +
            // ".accessLayer2, \"\");");
            for (Map.Entry<AttackStepName, String> entry : testingCase.attackStepStates.entrySet()) {
               AttackStepName attackStepName = entry.getKey();
               String attackStepState = entry.getValue();
               writer.println("      assertTrue(" + attackStepName.assetName.toLowerCase() + "." + attackStepName.attackStepName + ".ttc" + attackStepState + ");");
            }
            writer.println("   }");
         }
         writer.println("");
         writer.println("}");
         writer.close();

      }
      catch (FileNotFoundException e) {
         System.out.println("FileNotFoundException");
         e.printStackTrace();
      }
      catch (UnsupportedEncodingException e) {
         System.out.println("UnsupportedEncodingException");
         e.printStackTrace();
      }

   }

   protected void writeTestRunner(String javaFolderPath) {

      String testCodeFile = javaFolderPath + "/autoLang/test/TestRunner.java";
      try {
         writer = new PrintWriter(testCodeFile, "UTF-8");
         writer.println("import org.junit.runner.JUnitCore;\nimport org.junit.runner.Result;\nimport org.junit.runner.notification.Failure;\n");
         writer.print("public class TestRunner {\n   public static void main(String[] args) {\n      Result result = JUnitCore.runClasses(");
         // writer.print("testNetwork.class");
         writer.println(
               ");\n\n      for (Failure failure : result.getFailures()) {\n         System.out.println(failure.toString());\n      }\n\n      System.out.println(\"Executed \" + result.getRunCount() + \" cases.\");");
         writer.println(
               "       if (result.wasSuccessful()) {\n         System.out.println(\"All were successful.\");\n      }\n      else {\n         System.out.println(\"Some failed.\");\n      }\n\n   }\n}");
         writer.close();

      }
      catch (FileNotFoundException e) {
         System.out.println("FileNotFoundException");
         e.printStackTrace();
      }
      catch (UnsupportedEncodingException e) {
         System.out.println("UnsupportedEncodingException");
         e.printStackTrace();
      }

   }

   class AttackStepName {
      String assetName;
      String attackStepName;

      String attackPointName() {
         return assetName + "." + attackStepName;
      }
   }

   class TestingCase {
      Set<String>                 assetNames        = new HashSet<>();
      Set<AttackStepName>         attackPointNames  = new HashSet<>();
      Map<AttackStepName, String> defenseStepStates = new HashMap<>();
      Map<AttackStepName, String> attackStepStates  = new HashMap<>();
   }

}
