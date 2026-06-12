export type Risk = "high" | "med" | "low" | "origin";

export interface LineRef {
  line: number;
  snippet: string;
  verified: boolean;
  kind: "method-call" | "import" | "field-access" | "type-ref";
}

export interface ImpactFile {
  id: string;
  path: string;
  shortName: string;
  risk: Risk;
  score: number;
  dependents: number;
  cascade: string[];
  lineRefs: LineRef[];
  reasoning: string;
  breakingTests: string[];
  suggestedTests: string[];
}

export interface GraphEdge {
  source: string;
  target: string;
}

export const mockFiles: ImpactFile[] = [
  {
    id: "owner",
    path: "src/main/java/org/springframework/samples/petclinic/owner/Owner.java",
    shortName: "Owner.java",
    risk: "origin",
    score: 100,
    dependents: 12,
    cascade: ["Owner"],
    lineRefs: [
      { line: 42, snippet: "public class Owner extends Person {", verified: true, kind: "type-ref" },
      { line: 78, snippet: "public String getFirstName() {", verified: true, kind: "method-call" },
    ],
    reasoning:
      "Origin of the change. Renaming the Owner class will cascade into every consumer that imports, extends, or references it by type.",
    breakingTests: [],
    suggestedTests: [],
  },
  {
    id: "owner-controller",
    path: "src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java",
    shortName: "OwnerController.java",
    risk: "high",
    score: 92,
    dependents: 8,
    cascade: ["Owner", "OwnerController"],
    lineRefs: [
      { line: 56, snippet: "import org.springframework.samples.petclinic.owner.Owner;", verified: true, kind: "import" },
      { line: 124, snippet: "Owner owner = this.owners.findById(ownerId);", verified: true, kind: "type-ref" },
      { line: 187, snippet: "model.put(\"owner\", owner);", verified: false, kind: "field-access" },
    ],
    reasoning:
      "OwnerController binds the Owner type across 8 request mappings. A rename will break every Spring MVC route handler and the form-backing model attribute. Update @ModelAttribute names and Thymeleaf template refs in lockstep.",
    breakingTests: ["OwnerControllerTests.testInitCreationForm", "OwnerControllerTests.testProcessUpdateOwnerForm"],
    suggestedTests: ["Add IT covering /owners/{id}/edit round-trip after rename"],
  },
  {
    id: "owner-repository",
    path: "src/main/java/org/springframework/samples/petclinic/owner/OwnerRepository.java",
    shortName: "OwnerRepository.java",
    risk: "high",
    score: 88,
    dependents: 6,
    cascade: ["Owner", "OwnerRepository"],
    lineRefs: [
      { line: 38, snippet: "public interface OwnerRepository extends Repository<Owner, Integer> {", verified: true, kind: "type-ref" },
      { line: 61, snippet: "Page<Owner> findByLastName(@Param(\"lastName\") String lastName, Pageable pageable);", verified: true, kind: "method-call" },
    ],
    reasoning:
      "Spring Data repository typed on Owner. Rename forces a regeneration of the JPA query metamodel. JPQL referencing `Owner o` must be updated.",
    breakingTests: ["OwnerRepositoryTests.shouldFindOwnersByLastName"],
    suggestedTests: ["Verify @Query JPQL still resolves after rename"],
  },
  {
    id: "pet",
    path: "src/main/java/org/springframework/samples/petclinic/owner/Pet.java",
    shortName: "Pet.java",
    risk: "med",
    score: 64,
    dependents: 5,
    cascade: ["Owner", "Pet"],
    lineRefs: [
      { line: 52, snippet: "@ManyToOne @JoinColumn(name = \"owner_id\") private Owner owner;", verified: true, kind: "field-access" },
    ],
    reasoning:
      "Holds a @ManyToOne reference to Owner. Type rename only — column mapping is unaffected — but compile-time field declaration must change.",
    breakingTests: ["PetTests.testOwnerAssociation"],
    suggestedTests: [],
  },
  {
    id: "visit",
    path: "src/main/java/org/springframework/samples/petclinic/visit/Visit.java",
    shortName: "Visit.java",
    risk: "med",
    score: 51,
    dependents: 3,
    cascade: ["Owner", "Pet", "Visit"],
    lineRefs: [
      { line: 71, snippet: "Owner owner = this.pet.getOwner();", verified: false, kind: "method-call" },
    ],
    reasoning:
      "Indirect coupling through Pet → Owner navigation. Bob flagged a likely transient reference; AST could not confirm because the call chain spans two files.",
    breakingTests: [],
    suggestedTests: ["Add test asserting visit→pet→owner traversal still resolves"],
  },
  {
    id: "owner-form",
    path: "src/main/resources/templates/owners/createOrUpdateOwnerForm.html",
    shortName: "createOrUpdateOwnerForm.html",
    risk: "med",
    score: 47,
    dependents: 2,
    cascade: ["Owner", "OwnerController", "owner-form"],
    lineRefs: [
      { line: 14, snippet: "<form th:object=\"${owner}\" method=\"post\">", verified: false, kind: "field-access" },
    ],
    reasoning:
      "Thymeleaf template binds to ${owner}. If you also rename the @ModelAttribute key, this template will silently fail to bind.",
    breakingTests: [],
    suggestedTests: [],
  },
  {
    id: "clinic-service",
    path: "src/main/java/org/springframework/samples/petclinic/system/ClinicService.java",
    shortName: "ClinicService.java",
    risk: "low",
    score: 22,
    dependents: 2,
    cascade: ["Owner", "OwnerRepository", "ClinicService"],
    lineRefs: [
      { line: 95, snippet: "Collection<Owner> findOwners(String lastName);", verified: true, kind: "type-ref" },
    ],
    reasoning: "Service-layer facade exposes Owner in its method signature. Mechanical rename only.",
    breakingTests: [],
    suggestedTests: [],
  },
  {
    id: "owner-tests",
    path: "src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java",
    shortName: "OwnerControllerTests.java",
    risk: "low",
    score: 18,
    dependents: 0,
    cascade: ["Owner", "OwnerController", "OwnerControllerTests"],
    lineRefs: [
      { line: 44, snippet: "private Owner george() { ... }", verified: true, kind: "method-call" },
    ],
    reasoning: "Test fixture. Will fail to compile until rename is applied; no logical change required.",
    breakingTests: ["OwnerControllerTests.testInitCreationForm"],
    suggestedTests: [],
  },
];

export const mockEdges: GraphEdge[] = [
  { source: "owner", target: "owner-controller" },
  { source: "owner", target: "owner-repository" },
  { source: "owner", target: "pet" },
  { source: "owner", target: "clinic-service" },
  { source: "pet", target: "visit" },
  { source: "owner-controller", target: "owner-form" },
  { source: "owner-controller", target: "owner-tests" },
  { source: "owner-repository", target: "clinic-service" },
  { source: "owner", target: "visit" },
  { source: "owner", target: "owner-form" },
  { source: "owner", target: "owner-tests" },
];

export const riskColor = (r: Risk) =>
  r === "high"
    ? "var(--risk-high)"
    : r === "med"
      ? "var(--risk-med)"
      : r === "low"
        ? "var(--risk-low)"
        : "var(--origin)";
