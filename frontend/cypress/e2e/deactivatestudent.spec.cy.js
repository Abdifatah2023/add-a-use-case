// UC-STU-deactivate-student: the confirm dialog (step 4), cancelling it (5a), and what the SPA does with a success
// (step 6) and an already-deactivated answer (6a). The student API calls are stubbed, so these tests check the SPA;
// the backend's StudentDeactivationIntegrationTest covers who may deactivate and what is stored.
// Run against the dev stack (backend + `npm run dev`) with `npm run cy:open`.

const john = {
  id: 4,
  username: 'j.smith@abc.edu',
  firstName: 'John',
  lastName: 'Smith',
  email: 'j.smith@abc.edu',
  enabled: true,
  roles: 'student',
  sectionId: 2,
  sectionName: '2023-2024',
  teamId: 1,
  teamName: 'Team1'
}

const page = (students) => ({
  flag: true,
  code: 200,
  message: 'Find students successfully',
  data: { content: students, totalElements: students.length, number: 0, size: 10 }
})

describe('Deactivate a student', () => {
  beforeEach(() => {
    cy.login('b.gates@abc.edu', '123456')
    cy.intercept('POST', '**/students/search*', { body: page([john]) }).as('search')
    cy.intercept('GET', '**/students/4', {
      body: { flag: true, code: 200, message: 'Find student successfully', data: john }
    }).as('findJohn')
  })

  function openJohnAndChooseDeactivate() {
    cy.visit('/students')
    cy.wait('@search')
    cy.contains('.el-table__row', 'j.smith@abc.edu').find('button').click()
    cy.wait('@findJohn')
    cy.get('[data-cy=deactivate-student]').click()
  }

  it('names the student and lists every consequence before sending anything (step 4)', () => {
    cy.intercept('PUT', '**/students/4/deactivate', cy.spy().as('deactivate'))
    openJohnAndChooseDeactivate()

    cy.get('.deactivate-student-confirm [data-cy=deactivate-consequences]')
      .should('contain', 'Deactivate John Smith?')
      .and('contain', 'can no longer log in')
      .and('contain', 'ends on its next request')
      .and('contain', 'stays on the team')
      .and('contain', 'peer evaluations are kept')
      .and('contain', 'drop out of reminders')
      .and('contain', 'Teammates can no longer evaluate')
      .and('contain', 'can be reactivated later')
      .and('contain', 'deletion, which is permanent')
    cy.get('@deactivate').should('not.have.been.called')
  })

  it('sends nothing when the instructor cancels (5a)', () => {
    cy.intercept('PUT', '**/students/4/deactivate', cy.spy().as('deactivate'))
    openJohnAndChooseDeactivate()

    cy.get('.deactivate-student-confirm').contains('button', 'Cancel').click()

    cy.get('.deactivate-student-confirm').should('not.exist')
    cy.get('[data-cy=deactivate-student]').should('be.visible') // Still active, dialog still open
    cy.get('@deactivate').should('not.have.been.called')
  })

  it('confirms, closes the dialog and reloads the list with the student deactivated (step 6)', () => {
    cy.intercept('PUT', '**/students/4/deactivate', {
      body: {
        flag: true,
        code: 200,
        message: 'Deactivate student successfully',
        data: { ...john, enabled: false }
      }
    }).as('deactivate')
    openJohnAndChooseDeactivate()
    cy.intercept('POST', '**/students/search*', { body: page([{ ...john, enabled: false }]) }).as('reload')

    cy.get('.deactivate-student-confirm').contains('button', 'Confirm').click()

    cy.wait('@deactivate')
    cy.contains('.el-message', 'John Smith has been deactivated.')
    cy.get('[data-cy=deactivate-student]').should('not.exist') // Edit dialog closed
    cy.wait('@reload')
    cy.contains('.el-table__row', 'j.smith@abc.edu')
      .find('[data-cy=student-status]')
      .should('have.text', 'Deactivated')
  })

  it('shows the already-deactivated message when the student was deactivated meanwhile (6a)', () => {
    cy.intercept('PUT', '**/students/4/deactivate', {
      statusCode: 409,
      body: { flag: false, code: 409, message: 'This student is already deactivated.' }
    }).as('deactivate')
    openJohnAndChooseDeactivate()

    cy.get('.deactivate-student-confirm').contains('button', 'Confirm').click()

    cy.wait('@deactivate')
    cy.contains('.el-message', 'This student is already deactivated.')
  })
})
