/*
 * copyright 2012, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.resources.Resource;
import poke.server.resources.ResourceUtil;
import poke.server.storage.jdbc.DatabaseStorage;

import com.google.protobuf.ByteString;

import eye.Comm.Header;
import eye.Comm.Payload;
import eye.Comm.PhotoHeader;
import eye.Comm.PhotoHeader.ResponseFlag;
import eye.Comm.PhotoPayload;
import eye.Comm.Request;

public class JobResource implements Resource {
	protected DatabaseStorage storage = new DatabaseStorage() {
	};

	protected static Logger logger = LoggerFactory.getLogger("database");

	@Override
	public Request process(Request request) {

		logger.info("poke: " + request.getBody().getJobOp().getAction());

		/*
		 * String type = new String(); type =
		 * request.getBody().getJobOp().getAction().toString();
		 */

		logger.info("REQUEST :: " + request.toString());

		// Get the routing id
		// Routing routing_id = request.getHeader().getRoutingId();
		// Get the operation read/write/delete from request header
		int requesttype = request.getHeader().getPhotoHeader().getRequestType()
				.getNumber();

		Request.Builder rb = Request.newBuilder();
		Header.Builder header = request.getHeader().toBuilder();
		Payload.Builder pb = Payload.newBuilder();

		PhotoPayload.Builder fp = PhotoPayload.newBuilder();
		Request reply = null;
		logger.info("REQUEST TYPE :: " + requesttype);
		// Reading Image from the database
		if (requesttype == PhotoHeader.RequestType.read.getNumber()) {
			logger.info("REQUEST out READ :: " + request.getHeader());
			logger.info("REQUEST   " + request.getBody());
			ByteString value = storage.readImage(request.getBody().getPhotoPayload().getUuid());

			if (value != null) {
				/*
				 * rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
				 * ResponseFlag.success_VALUE, "Image Retived successfully"));
				 */
				header.setReplyMsg("Image Retrived successfully");
				rb.setHeader(header);
				rb.setBody(request.getBody());
				fp.setData(value);
			} else {
//				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
//						ResponseFlag.failure, "Error in Retrive image"));
				
				header.setReplyMsg("Error in Retrive image");
				PhotoHeader.Builder phdrBldr = PhotoHeader.newBuilder();
				phdrBldr.setResponseFlag(ResponseFlag.failure);
				header.setPhotoHeader(phdrBldr);
				rb.setHeader(header);
			}
			pb.setPhotoPayload(fp.build());
			rb.setBody(pb.build());
			reply = rb.build();
		}

		// Writing Image to the Database
		else if (requesttype == PhotoHeader.RequestType.write.getNumber()) {
			logger.info("REQUEST out WRITE :: "
					+ request.getBody().getPhotoPayload().getName());
			logger.info("REQUEST   "
					+ request.getBody().getPhotoPayload().getData());

			boolean status = storage.addImageWithId(request.getBody().getPhotoPayload().getName(),
					request.getBody().getPhotoPayload().getData(),
					request.getBody().getPhotoPayload().getUuid());
			if (status) {
				/*
				 * rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
				 * ResponseFlag.success_VALUE, "Image added successfully"));
				 */
				header.setReplyMsg("Image write successful");
				rb.setHeader(header);
				rb.setBody(request.getBody());
			} else {
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						ResponseFlag.failure, "Error adding image"));
			}

			fp.setUuid(request.getBody().getPhotoPayload().getUuid());
			pb.setPhotoPayload(fp.build());
			rb.setBody(pb.build());
			reply = rb.build();
		} else if (requesttype == PhotoHeader.RequestType.delete.getNumber()) {
			logger.info("REQUEST out Delete :: " + request.getHeader());
			logger.info("REQUEST   " + request.getBody());

			boolean value = storage.deleteImage(request.getBody()
					.getPhotoPayload().getUuid());

			if (value == true) {
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						ResponseFlag.success, "Image Deleted successfully"));
				rb.setBody(request.getBody());
			} else {
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(),
						ResponseFlag.failure, "Error in Delete image"));
			}

			pb.setPhotoPayload(fp.build());
			rb.setBody(pb.build());
			reply = rb.build();

		}

		logger.info("Reply :: " + reply.toString());

		return reply;
	}

}
