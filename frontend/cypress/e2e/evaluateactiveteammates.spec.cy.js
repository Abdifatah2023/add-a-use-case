// UC-EVA-submit-evaluation step 2 (BR-evaluatee-active): the peer evaluation form offers only active team members,
// so a teammate deactivated through UC-STU-deactivate-student is not listed. The team-members and own-evaluations
// calls are stubbed; the backend's EvaluationIntegrationTest covers the 9a refusal itself.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const member = (id, firstName, lastName, enabled) => ({
  id,
  username: `${firstName[0].toLowerCase()}.${lastName.toLowerCase()}@abc.edu`,
  firstName,
  lastName,
  email: `${firstName[0].toLowerCase()}.${lastName.toLowerCase()}@abc.edu`,
  enabled,
  roles: 'student',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1'
})

describe('Peer evaluation form offers only active teammates', () => {
  beforeEach(() => {
    cy.login('j.smith@abc.edu', '123456')
    cy.intercept('GET', '**/students/teams/1', {
      body: {
        flag: true,
        code: 200,
        message: 'Find students by team id successfully',
        data: [
          member(4, 'John', 'Smith', true),
          member(5, 'Eric', 'Hudson', false), // Deactivated
          member(6, 'Jerry', 'Moon', true)
        ]
      }
    }).as('team')
    cy.intercept('GET', '**/evaluations/evaluators/4/week/*', {
      body: { flag: true, code: 200, message: 'Find peer evaluations successfully', data: [] }
    }).as('ownEvaluations')
  })

  it('lists only teammates whose account is active (step 2)', () => {
    cy.visit('/evaluations/submit-evaluations')
    cy.wait('@team')
    cy.wait('@ownEvaluations')

    cy.get('.el-table__body').should('contain', 'John Smith').and('contain', 'Jerry Moon')
    cy.get('.el-table__body').should('not.contain', 'Eric Hudson')
  })
})
