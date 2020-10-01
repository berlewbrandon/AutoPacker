import {
	Button,
	Col,
	Collapse,
	Descriptions,
	Layout,
	Modal,
	PageHeader,
	Row,
	Tag,
	Typography,
} from "antd";
import React, { useEffect } from "react";
import { useDispatch, useSelector } from "react-redux";
import { createAlert } from "../../../store/actions/generalActions";
import { useKeycloak } from "@react-keycloak/web";
import axios from "axios";

import RequestEditForm from "./components/RequestEditForm/RequestEditForm";

function Submissions() {
	// State
	const [requests, setRequests] = React.useState([]);
	const [openDeleteModal, setOpenDeleteModal] = React.useState(false);
	const [openEditModal, setOpenEditModal] = React.useState();
	const [refreshList, setRefreshList] = React.useState(false);

	// Modal info state
	const [selectedRequest, setSelectedRequest] = React.useState(null);

	// Import sub components from antd
	const { Paragraph } = Typography;
	const { Content } = Layout;
	const { Panel } = Collapse;

	const [keycloak] = useKeycloak();

	const organizationName = sessionStorage.getItem("selectedOrganizationName");

	const dispatch = useDispatch();

	useEffect(() => {
		axios({
			method: "get",
			url:
				process.env.REACT_APP_APPLICATION_URL +
				process.env.REACT_APP_GENERAL_API +
				"/organization/" +
				organizationName +
				"/project-applications/" +
				keycloak.idTokenParsed.preferred_username,
			headers: {
				Authorization: keycloak.token !== null ? `Bearer ${keycloak.token}` : undefined,
			},
		}).then(function (response) {
			console.log(response.data);
			setRequests(response.data);
		});

		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [refreshList]);

	const toggleRefresh = () => {
		setRefreshList(!refreshList);
	};

	const routes = [
		{
			path: "organization/dashboard",
			breadcrumbName: "Dashboard",
		},
		{
			path: "organization/project-requests",
			breadcrumbName: "Your Project Submissions",
		},
	];

	const deleteRequest = (id) => {
		console.log("DELETE PROCESS STARTED...");

		// TODO Change method to DELETE and equivalent on general-api backend service
		axios({
			method: "get",
			url:
				process.env.REACT_APP_APPLICATION_URL +
				process.env.REACT_APP_GENERAL_API +
				"/organization/" +
				organizationName +
				"/delete-project-application/" +
				id,
			headers: {
				Authorization: keycloak.token !== null ? `Bearer ${keycloak.token}` : undefined,
			},
		})
			.then(() => {
				dispatch(
					createAlert(
						"Request Deleted",
						"You have successfully deleted the project request",
						"success",
						true
					)
				);
				toggleRefresh();
				setOpenDeleteModal(false);
			})
			.catch(() => {
				dispatch(
					createAlert(
						"Request Deletion Failed",
						"Failed to delete the project request",
						"error",
						true
					)
				);
				setOpenDeleteModal(false);
			});
	};

	return (
		<div style={{ width: "100%" }}>
			<PageHeader
				style={{
					border: "1px solid rgb(235, 237, 240)",
					backgroundColor: "#FFFFFF",
				}}
				title="Your Project Submissions"
				breadcrumb={{ routes }}
			>
				<Paragraph>
					List of all the project submissions you have with this organization
				</Paragraph>
			</PageHeader>
			{/* Can add the Content thingy to route to be common, but might want to do something about the breadcrumb thingy */}
			<Content
				style={{
					margin: "24px 20px",
				}}
			>
				<div
					style={{
						padding: 24,
						background: "#fff",
						maxWidth: 1000,
						width: "100%",
						margin: "0 auto",
					}}
				>
					<Collapse>
						{requests.map((request) => (
							<Panel
								header={
									request.member.username +
									" - " +
									request.organizationProject.name
								}
								key={request.id}
								extra={request.created}
							>
								<Row gutter={[24, 0]}>
									<Col xs={24} md={5}>
										<img
											style={{
												width: "100%",
												border: "1px solid black",
											}}
											src={
												"https://res.cloudinary.com/hkf2ycaep/image/fetch/d_project-placeholder.png,f_auto/https:/assets/project-placeholder-b90804f0a659d3f283c62d185d49635da22a5b8bbfb7e985f0d0390201f9d2b1.png"
											}
											alt="Applicant Profile"
										/>
									</Col>
									<Col xs={24} md={19}>
										<Descriptions
											title="Project Application Details"
											layout="horizontal"
										>
											<Descriptions.Item label="Submitted By">
												{request.member.username}
											</Descriptions.Item>
											<Descriptions.Item label="Project Name">
												{request.organizationProject.name}
											</Descriptions.Item>
											<Descriptions.Item label="Type">
												{request.organizationProject.type}
											</Descriptions.Item>

											<Descriptions.Item label="Tags">
												{request.organizationProject.tags.length > 1 ? (
													request.organizationProject.tags
														.split(",")
														.map((tag) => (
															<span
																key={tag}
																style={{ display: "inline-block" }}
															>
																<Tag color="blue">{tag}</Tag>
															</span>
														))
												) : (
													<div />
												)}
											</Descriptions.Item>
										</Descriptions>

										<Descriptions layout="horizontal">
											<Descriptions.Item label="Description">
												{request.organizationProject.description}
											</Descriptions.Item>
										</Descriptions>

										<Descriptions layout="horizontal">
											<Descriptions.Item label="Authors">
												<b>{request.organizationProject.authors}</b>
											</Descriptions.Item>
										</Descriptions>

										<Descriptions layout="horizontal">
											<Descriptions.Item label="Links">
												<b>{request.organizationProject.links}</b>
											</Descriptions.Item>
										</Descriptions>

										<Descriptions layout="horizontal">
											<Descriptions.Item label="Comment">
												{request.comment}
											</Descriptions.Item>
										</Descriptions>
									</Col>
								</Row>

								<div style={{ float: "right", padding: 10 }}>
									<Button
										style={{ marginRight: 10 }}
										type="danger"
										onClick={() => {
											setSelectedRequest(request);
											setOpenDeleteModal(true);
										}}
									>
										Delete Request
									</Button>
									<Button
										onClick={() => {
											setSelectedRequest(request);
											setOpenEditModal(true);
										}}
									>
										Edit
									</Button>
								</div>
							</Panel>
						))}
					</Collapse>
					{/* Delete Modal */}
					{selectedRequest !== null ? (
						<Modal
							title={
								'Delete "' + selectedRequest.organizationProject.name + '" request?'
							}
							centered
							visible={openDeleteModal}
							onOk={() => deleteRequest(selectedRequest.organizationProject.id)}
							onCancel={() => setOpenDeleteModal(false)}
						>
							<p>Are you sure you want to delete this project request?</p>
						</Modal>
					) : (
						<div />
					)}
					{/* Edit Modal */}
					{selectedRequest !== null ? (
						<Modal
							title={'Edit "' + selectedRequest.organizationProject.name + '"?'}
							centered
							visible={openEditModal}
							onCancel={() => setOpenEditModal(false)}
							footer={null}
						>
							<RequestEditForm
								request={selectedRequest}
								setOpenEditModal={setOpenEditModal}
								toggleRefresh={toggleRefresh}
							/>
						</Modal>
					) : (
						<div />
					)}
				</div>
			</Content>
		</div>
	);
}

export default Submissions;
