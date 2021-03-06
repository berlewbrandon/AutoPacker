package no.autopacker.general.entity.organization;

import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@NoArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @NotEmpty
    @Column(unique = true)
    private String name;

    private String image;

    private String description;

    @OneToMany(mappedBy = "organization")
    private List<OrganizationProject> organizationProjects;

    @OneToMany(mappedBy = "organization")
    private List<Member> members;

    @OneToMany(mappedBy = "organization")
    private List<MemberApplication> memberApplications;

    @OneToMany(mappedBy = "organization")
    private List<ProjectApplication> projectApplications;

    public Organization(String name, String desc) {
        this.name = name;
        this.description = desc;
        this.organizationProjects = new ArrayList<>();
        this.members = new ArrayList<>();
    }

}
